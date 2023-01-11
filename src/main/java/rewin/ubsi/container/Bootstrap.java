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

package rewin.ubsi.container;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import rewin.ubsi.common.IOData;
import rewin.ubsi.common.JedisUtil;
import rewin.ubsi.common.LogUtil;
import rewin.ubsi.common.Util;
import rewin.ubsi.consumer.Config;
import rewin.ubsi.consumer.Context;
import rewin.ubsi.consumer.Register;

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 *  UBSI微服务容器的主类
 */
public class Bootstrap {

    final static int MAX_BACKLOG = 128;
    final static int MIN_BACKLOG = 16;
    final static int MAX_IOTHREADS = 128;
    final static int MIN_IOTHREADS = 0;
    final static int MAX_WORKTHREADS = 256;
    final static int MIN_WORKTHREADS = 8;
    final static int MAX_OVERLOAD = 10000;
    final static int MIN_OVERLOAD = 10;
    final static int MAX_FORWARD = 600;
    final static int MIN_FORWARD = 0;

    static String   Host;               // 本机的主机名
    static int      Port = 0;           // 监听的端口
    static int      BackLog = 128;      // 监听端口的等待队列

    static int      IOThreads = 0;      // I/O线程数
    static int      WorkThreads = 20;   // 工作线程数
    static int      TimeoutFuse = 0;    // 当前接口超时的数量N个后熔断
    static int      Overload = 100;     // 等待处理请求的最大数量
    static int      Forward = 60;       // 转发请求的超时时间
    static List<Info.ForwardService> ForwardDoor = null;// 需注册的"转发"微服务

    static Channel MainChannel = null;              // 端口监听Socket
    static NioEventLoopGroup IOGroup = null;        // I/O线程池

    static ExecutorService WorkGroup = null;        // 工作线程池

    static AtomicLong SocketConnected = new AtomicLong(0);  // 计数器：总连接次数
    static AtomicLong SocketDisconnect = new AtomicLong(0); // 计数器：总断开次数
    static AtomicLong RequestTotal = new AtomicLong(0);     // 计数器：总请求次数
    static AtomicLong RequestOverload = new AtomicLong(0);  // 计数器：过载丢弃次数
    static AtomicLong RequestDeal = new AtomicLong(0);      // 计数器：请求处理次数
    static AtomicLong RequestOver = new AtomicLong(0);      // 计数器：请求完成次数
    static AtomicLong RequestForward = new AtomicLong(0);   // 计数器：请求转发次数

    static ConcurrentMap<String, Service> ServiceMap = new ConcurrentHashMap<>();       // 被加载的所有服务
    static ConcurrentLinkedQueue<Filter> FilterList = new ConcurrentLinkedQueue<>();    // 被加载的所有过滤器
    static String ServicePath = ".";    // 当前的运行目录
    static String ContainerVersion;     // 容器控制器的接口版本
    static Timer ServiceTimer = null;   // Service定时任务
    static Config.LogAccess[] LogForce = null;        // 强制记录container请求日志的服务列表

    final static String         MODULE_PATH = "rewin.ubsi.modules";
    final static String         LOG_APPTAG = "rewin.ubsi.container";
    public final static String  LOG_APPID = "rewin.ubsi.container";
    public final static int     DEFAULT_PORT = 7112;    // 缺省的端口号

    /* 查找过滤器 */
    static Filter findFilter(String name) {
        Iterator<Filter> iter = FilterList.iterator();
        while ( iter.hasNext() ) {
            Filter filter = iter.next();
            if ( filter.JClass.getName().equals(name) )
                return filter;
        }
        return null;
    }
    /* 查找模块 */
    static Filter findModule(String name) {
        Service srv = ServiceMap.get(name);
        if ( srv != null )
            return srv;
        return findFilter(name);
    }

    /* 日志 */
    static void log(int type, String tips, Object body) {
        LogUtil.log(type, LOG_APPTAG, LOG_APPID, new Throwable(), 1, tips, body);
    }

    /** 获取本机IP地址 */
    public static String resolveHost() throws Exception {
        // 从系统参数中获得地址 java -Dubsi.host=xxxx
        String host = Util.checkEmpty(System.getProperty("ubsi.host"));
        if ( host != null )
            return host;
        try {
            Object[] redis_addr = Context.getRedisAddress();
            if ( redis_addr != null ) {
                Socket sock = new Socket();
                sock.connect(new InetSocketAddress((String)redis_addr[0], (Integer)redis_addr[1]), 1000);
                if ( sock.isConnected() ) {
                    try {
                        InetAddress local_addr = sock.getLocalAddress();
                        if ( !local_addr.isLoopbackAddress() )
                            return local_addr.getHostAddress();
                    } finally {
                        sock.close();
                    }
                }
            }
        } catch (Exception e) {
        }
        return InetAddress.getLocalHost().getHostName().toLowerCase();
    }

    /** 从系统参数中获得端口 java -Dubsi.port=7112 */
    public static int resolvePort(int defaultPort) throws Exception {
        String port = Util.checkEmpty(System.getProperty("ubsi.port"));
        if ( port != null ) {
            defaultPort = Integer.parseInt(port);
            if ( defaultPort <= 256 || defaultPort >= 65536 )
                throw new Exception("invalid port");
        }
        return defaultPort;
    }

    /** 启动容器 */
    public synchronized static void start() throws Exception {
        ServicePath = new File(".").getCanonicalPath();                 // 取当前运行目录
        Context.setLogApp(InetAddress.getLocalHost().getHostName().toLowerCase() + "#---", LOG_APPTAG);
        Context.startup(ServicePath);           // 初始化UBSI客户端

        LibManager.init();

        // 加载容器控制器
        Service controller = Service.load("rewin.ubsi.container.Controller", null, null);
        ContainerVersion = Util.getVersion(controller.Version) + (controller.Release ? "" : "-SNAPSHOT");
        Bootstrap.ServiceMap.put("", controller);
        controller.start("");

        // 启动定时任务
        Service.FlushRegister = true;
        ServiceTimer = new Timer();
        ServiceTimer.schedule(new Service.TimerDealer(), 0, Context.REGISTER_TIMER);

        IOGroup = new NioEventLoopGroup(IOThreads);
        WorkGroup = Executors.newFixedThreadPool(WorkThreads);
        ServerBootstrap boot = new ServerBootstrap();
        boot.group(IOGroup).channel(NioServerSocketChannel.class);
        boot.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel sc) throws Exception {
                if ( !ServiceAcl.check(sc.remoteAddress().getAddress()) )
                    throw new Exception("connect denied");
                sc.pipeline().addLast(new IdleStateHandler(Context.BEATHEART_RECV, 0, 0, TimeUnit.SECONDS))
                        .addLast(new IOData.Decoder(), new IOHandler());
            }
        });
        boot.option(ChannelOption.SO_BACKLOG, BackLog);  // 连接请求的缓冲数量
        boot.childOption(ChannelOption.TCP_NODELAY, true);

        ChannelFuture future = boot.bind(Port).sync();	// 绑定监听端口
        MainChannel = future.channel();
        MainChannel.closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture channelFuture) throws Exception {
                if ( MainChannel != null )
                    stop();     // 监听端口被关闭，服务结束
            }
        });
        log(LogUtil.INFO, "startup", ContainerVersion);
    }

    /** 关闭容器 */
    public synchronized static void stop() throws Exception {
        Channel mainChannel = MainChannel;
        if ( mainChannel != null ) {
            MainChannel = null;
            try { mainChannel.close().sync(); } catch (Exception e) {}  // 关闭监听端口
        }

        if ( WorkGroup != null ) {
            WorkGroup.shutdown();           // 关闭工作线程池（必须在关闭I/O线程池之前，等待任务队列处理完成）
            WorkGroup = null;
        }
        if ( IOGroup != null ) {
            IOGroup.shutdownGracefully();   // 关闭I/O线程池（若阻塞会导致无法访问docker@windows，此时需重启docker容器）
            IOGroup = null;
        }

        if ( ServiceTimer != null ) {
            ServiceTimer.cancel();          // 关闭定时任务
            ServiceTimer = null;
        }
        try {   // 删除注册表项
            String reg_key = Bootstrap.Host + "#" + Bootstrap.Port;
            Context.delRegister(Context.REG_CONTAINER, reg_key);
            JedisUtil.publish(Context.CHANNEL_NOTIFY, reg_key + "|-");
        } catch (Exception e) {}

        try { ServiceMap.get("").stop(""); } catch (Exception e) {}     // 关闭控制器

        LibManager.close();

        log(LogUtil.INFO, "shutdown", ContainerVersion);
        Context.shutdown();                 // 关闭Consumer
    }

    /** 容器启动入口 */
    public static void main(String[] args) throws Exception {
        try {
            start();
            Runtime.getRuntime().addShutdownHook(new Thread() {
                // JVM退出时的Hook
                public void run() {
                    try { Bootstrap.stop(); } catch (Exception e) {}
                }
            });
        } catch (Exception e) {
            log(LogUtil.ERROR, "startup", e.toString());
            stop();
            throw e;
        }
    }

}
