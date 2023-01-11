/*
 * Copyright 1999-2022 Rewin Network Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rewin.ubsi.cli;

import org.dom4j.io.SAXReader;
import rewin.ubsi.common.JsonCodec;
import rewin.ubsi.common.Util;
import rewin.ubsi.consumer.Context;
import rewin.ubsi.consumer.ErrorCode;
import rewin.ubsi.container.Bootstrap;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 命令行工具：压力测试工具
 */
public class Stress {

    static String serviceName;
    static Object[] entryAndParams;
    static int timeout = 0;
    static int batchSize = 100;
    static int threads = 1;

    static AtomicInteger count = new AtomicInteger(0);
    static long startTime = 0;
    static boolean shutdown = false;

    static AtomicLong send = new AtomicLong(0);
    static AtomicLong ok = new AtomicLong(0);
    static AtomicLong err_s = new AtomicLong(0);
    static AtomicLong err_c = new AtomicLong(0);

    static ConcurrentSkipListSet<Integer> codes = new ConcurrentSkipListSet<>();    // 请求返回时的错误码
    static ConcurrentSkipListSet<String> msgs = new ConcurrentSkipListSet<>();      // 请求发送时的错误信息

    /* 请求结果的回调 */
    static Context.ResultNotify reqNotify = new Context.ResultNotify() {
        @Override
        public void callback(int code, Object result) {
            if ( code == ErrorCode.OK )
                ok.incrementAndGet();
            else {
                err_s.incrementAndGet();
                if ( !codes.contains(code) ) {
                    codes.add(code);
                    System.out.println("\n~~~ result error: " + result + ", ErrorCode: " + code);
                }
            }
        }
    };

    /* 工作线程，负责发送请求 */
    static class WorkThread extends Thread {
        public void run() {
            count.incrementAndGet();
            synchronized (serviceName) {
                try { serviceName.wait(); } catch (Exception e) {}
            }

            while ( !shutdown ) {
                if ( send.get() - batchSize > err_s.get() + err_c.get() + ok.get() ) {
                    try { sleep(1); } catch (Exception e) {}
                    continue;
                }
                send.incrementAndGet();
                try {
                    Context context = Context.request(serviceName, entryAndParams);
                    context.setTimeout(timeout);
                    context.callAsync(reqNotify, false);
                } catch (Exception e) {
                    err_c.incrementAndGet();
                    String msg = e.getMessage();
                    if ( !msgs.contains(msg) ) {
                        msgs.add(msg);
                        System.out.println("\n~~~ request error: " + msg);
                    }
                }
                yield();
            }
            count.decrementAndGet();
        }
    }

    /** 主程序入口 */
    public static void main(String[] args) throws Exception {
        Context context = null;
        boolean xml = false;
        if (args.length > 0) {
            try {
                String reqFile = args[0];
                if (args.length > 1)
                    batchSize = Integer.parseInt(args[1]);
                if ( args.length > 2 )
                    threads = Integer.parseInt(args[2]);
                int dot = reqFile.lastIndexOf('.');
                if ( dot >= 0 && "xml".equalsIgnoreCase(reqFile.substring(dot+1)) )
                    xml = true;
                context = xml ? Request.fromXml(new SAXReader().read(new File(reqFile))) : Request.fromJson(reqFile);
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }

        if ( context == null || Util.checkEmpty(context.getService()) == null  || Util.checkEmpty(context.getEntry()) == null ) {
            System.out.println("\nUsage: stress request.json|xml [" + batchSize + "<batch-size>] [" + threads + "<threads>]\n");
            if ( xml ) {
                System.out.println("xml file format:");
                System.out.println("\t<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
                System.out.println(Request.XML_REQUEST);
            } else {
                System.out.println("json file format:");
                System.out.println(Request.JSON_REQUEST);
            }
            return;
        }

        serviceName = context.getService();
        entryAndParams = new Object[1 + context.getParamCount()];
        entryAndParams[0] = context.getEntry();
        for ( int i = 1; i < entryAndParams.length; i ++ )
            entryAndParams[i] = context.getParam(i-1);
        timeout = context.getTimeout();

        Context.setLogApp(InetAddress.getLocalHost().getHostName(), "rewin.ubsi.cli.Stress");
        Context.startup(".");
        System.out.print("\n" + serviceName + ":" + entryAndParams[0] + "(): ");
        try {
            Object res = context.call();
            Request.printJson(JsonCodec.encodeType(res));
        } catch (Exception e) {
            System.out.println("\n");
            e.printStackTrace();
        }

        for ( int i = 0; i < threads; i ++ )
            new WorkThread().start();
        while(count.get() < threads)
            try { Thread.sleep(10); } catch (Exception e) {}

        Context.LogNoRouting = false;
        Runtime.getRuntime().addShutdownHook(new Thread() {
            // JVM退出时的Hook
            public void run() {
                System.out.println("\nstop stress testing ...\n");
                shutdown = true;
                while ( true ) {
                    int thread_count = count.get();
                    long send_all = send.get();
                    long ok_all = ok.get();
                    long err_s_all = err_s.get();
                    long err_c_all = err_c.get();
                    System.out.println("thread-" + thread_count +
                            " over: " + send_all + "(all) / " +
                            ok_all + "(ok) / " +
                            (err_s_all + err_c_all) + "(err) --- " +
                            ((System.currentTimeMillis() - startTime) / 1000) + "s(time)");
                    if ( thread_count == 0 && (send_all == err_s_all + err_c_all + ok_all) )
                        break;
                    try { Thread.sleep(1000); } catch (Exception e) {}
                }
                System.out.println();
                Context.shutdown();
            }
        });

        System.out.println("\nstart stress by " + batchSize + " / " + threads + ", CTRL-C to exit ...\n");
        synchronized (serviceName) {
            serviceName.notifyAll();
        }
        startTime = System.currentTimeMillis();

        long pre_t = startTime;
        long pre_send = 0;
        long pre_ok = 0;
        long pre_err_s = 0;
        long pre_err_c = 0;
        try { Thread.sleep(3000); } catch (Exception e) {}
        while ( !shutdown ) {
            long cur_t = System.currentTimeMillis();
            long cur_send = send.get();
            long cur_ok = ok.get();
            long cur_err_s = err_s.get();
            long cur_err_c = err_c.get();
            System.out.println("send: " + cur_send + "/" + (cur_send - pre_send) + ", " +
                    "ok: " + cur_ok + "/" + (cur_ok - pre_ok) + ", " +
                    "err-s: " + cur_err_s + "/" + (cur_err_s - pre_err_s) + ", " +
                    "err-c: " + cur_err_c + "/" + (cur_err_c - pre_err_c) + ", " +
                    "time: " + (cur_t - pre_t) + ", " +
                    "rate: " + ((cur_ok - pre_ok) * 1000 / (cur_t - pre_t)) + "/s"
            );
            pre_t = cur_t;
            pre_send = cur_send;
            pre_ok = cur_ok;
            pre_err_s = cur_err_s;
            pre_err_c = cur_err_c;
            try { Thread.sleep(3000); } catch (Exception e) {}
        }
    }
}
