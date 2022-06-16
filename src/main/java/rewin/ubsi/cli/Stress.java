package rewin.ubsi.cli;

import org.dom4j.io.SAXReader;
import rewin.ubsi.common.JsonCodec;
import rewin.ubsi.consumer.Context;
import rewin.ubsi.consumer.ErrorCode;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 命令行工具：压力测试工具
 */
public class Stress {

    static String serviceName;
    static Object[] entryAndParams;
    static int batchSize = 100;
    static boolean shutdown = false;

    static AtomicLong send = new AtomicLong(0);
    static AtomicLong ok = new AtomicLong(0);
    static AtomicLong err = new AtomicLong(0);

    static ConcurrentSkipListSet<Integer> codes = new ConcurrentSkipListSet<>();

    /* 请求结果的回调 */
    static Context.ResultNotify reqNotify = new Context.ResultNotify() {
        @Override
        public void callback(int code, Object result) {
            if ( code == ErrorCode.OK )
                ok.incrementAndGet();
            else {
                err.incrementAndGet();
                if ( !codes.contains(code) ) {
                    codes.add(code);
                    System.out.println("\n~~~ request error: " + result + ", ErrorCode: " + code);
                }
            }
        }
    };

    /* 工作线程，负责发送请求 */
    static class WorkThread extends Thread {
        public void run() {
            System.out.println("\nstart stress testing ...");
            while ( !shutdown ) {
                long count_ok = ok.get();
                long count_err = err.get();
                long count_send = send.get();
                int count = (int)(count_send - count_ok - count_err);
                for ( int i = count; i < batchSize; i ++ ) {
                    try {
                        Context context = Context.request(serviceName, entryAndParams);
                        context.setTimeout(0);
                        context.callAsync(reqNotify, false);
                        send.incrementAndGet();
                    } catch (Exception e) {
                        System.out.println("\nwork thread error: " + e.getMessage());
                        e.printStackTrace();
                        shutdown = true;
                        break;
                    }
                }
            }
            System.out.println("\nstress testing stopped!");
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
                int dot = reqFile.lastIndexOf('.');
                if ( dot >= 0 && "xml".equalsIgnoreCase(reqFile.substring(dot+1)) )
                    xml = true;
                context = xml ? Request.fromXml(new SAXReader().read(new File(reqFile))) : Request.fromJson(reqFile);
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }

        if ( context == null ) {
            System.out.println("\nUsage: stress request.json|xml [batch-size=" + batchSize + "]\n");
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

        Context.setLogApp(InetAddress.getLocalHost().getHostName(), "rewin.ubsi.cli.Stress");
        Context.startup(".");
        System.out.print("\n" + serviceName + ":" + entryAndParams[0] + "(): ");
        try {
            Object res = context.call();
            Request.printJson(JsonCodec.encodeType(res));
        } catch (Exception e) {
            System.out.println("\n");
            e.printStackTrace();
            Context.shutdown();
            return;
        }

        new WorkThread().start();
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        while ( !shutdown ) {
            try {
                long pre_t = System.currentTimeMillis();
                long pre_send = send.get();
                long pre_ok = ok.get();
                long pre_err = err.get();
                String str = input.readLine();
                if (str == null || !str.isEmpty() )
                    shutdown = true;
                long cur_t = System.currentTimeMillis();
                long cur_send = send.get();
                long cur_ok = ok.get();
                long cur_err = err.get();
                System.out.println("--- send: " + (cur_send - pre_send) + ", " +
                        "err: " + (cur_err - pre_err) + ", " +
                        "ok: " + (cur_ok - pre_ok) + ", " +
                        (cur_ok - pre_ok) * 1000 / (cur_t - pre_t) + "/s"
                );
            } catch (Exception e) {
                System.out.println("\nmain thread error: " + e.getMessage());
                e.printStackTrace();
                shutdown = true;
            }
        }

        Thread.sleep(1000);
        System.out.println();
        for ( int i = 0; i < 10; i ++ ) {
            System.out.println("main thread over: " + send.get() + " / " + ok.get() + " / " + err.get());
            if ( send.get() == ok.get() + err.get() )
                break;
            Thread.sleep(1000);
        }
        Context.shutdown();
    }
}
