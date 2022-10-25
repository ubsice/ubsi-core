package rewin.ubsi.container;

import rewin.ubsi.annotation.USEntry;
import rewin.ubsi.annotation.USParam;
import rewin.ubsi.common.JedisUtil;
import rewin.ubsi.common.Util;
import rewin.ubsi.consumer.Config;
import rewin.ubsi.consumer.Context;
import rewin.ubsi.consumer.Register;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * UBSI监控接口的数据对象
 */
public class Info {

    /** filter的当前运行状态 */
    public static class FRuntime {
        public String   class_name;         // Java类名字
        public String   tips;               // 说明
        public String   version;            // 版本
        public boolean  release;            // 发行状态
        public int      status;             // 运行状态
        public int      time_status;        // 状态的时间
        public boolean  dealing_timeout;    // 正在处理的请求中是否有超时的
    }

    /** 微服务的当前运行状态 */
    public static class SRuntime extends FRuntime {
        public long     request_over;       // 总处理数量
        public long     request_error;      // 总错误数量
        public int      request_dealing;    // 正在处理的数量
        public int      singleton;          // 是否单例
    }

    /** 容器当前的运行状态 */
    public static class Runtime {
        public int      client_connection;  // 当前连接数
        public long     request_overload;   // 总丢弃数量
        public long     request_over;       // 总处理数量（包含转发/失败等）
        public long     request_forward;    // 总转发数量
        public int      request_dealing;    // 正在处理的数量
        public int      request_waiting;    // 等待处理的数量

        public boolean  redis_enable;       // Redis是否连接
        public int      redis_conn_active;  // Redis连接活动数量
        public int      redis_conn_idle;    // Redis连接空闲数量

        public Map<String, SRuntime> services = new HashMap<>();    // 各个服务的运行状态
        public List<FRuntime> filters;      // 各个Filter的运行状态
    }

    /** Controller的运行信息 */
    public static class Controller {
        public String   os_name;            // 操作系统
        public String   run_path;           // 运行目录
        public String   java_version;       // Java版本
        public int      memory_free;        // JVM可用内存，MB
        public int      memory_total;       // JVM总内存，MB
        public int      processors;         // CPU核数
        public int      threads;            // JVM线程数
        public String   redis_conn;         // Redis连接数量: "活动数, 空闲数"
        public long[]   redis_time;         // Redis时间戳: [ 连接时间, 断开时间 ]
        public long[]   heartbeat;          // 心跳时间：[ 容器心跳最大间隔, 容器最大间隔时间戳, 接收心跳最大间隔, 接收最大间隔时间戳 ]
        public String   pid;                // 进程号
        public List<String> top;            // top命令的输出
        public Map<String, Register.Container> register;    // 动态路由表
        public Map      consumer_statistics;// Consumer请求统计
    }
    /** 获取Controller的运行信息 */
    public static Controller getController(Controller controller) {
        controller.java_version = System.getProperty("java.version");
        controller.os_name = System.getProperty("os.name");
        controller.run_path = Bootstrap.ServicePath;
        controller.processors = java.lang.Runtime.getRuntime().availableProcessors();
        controller.memory_free = (int)(java.lang.Runtime.getRuntime().freeMemory() / (1024*1024));
        controller.memory_total = (int)(java.lang.Runtime.getRuntime().totalMemory() / (1024*1024));
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        controller.threads = bean.getThreadCount();
        controller.redis_time = new long[] { JedisUtil.timeInit, JedisUtil.timeClose };
        controller.heartbeat = new long[] { Service.timeHeartbeatMax, Service.timeHeartbeatMaxAt, Context.timeHeartbeatMax, Context.timeHeartbeatMaxAt };
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        controller.pid = runtime.getName();
        try { controller.top = Util.runCommand(20, "top", "-b", "-n 1"); } catch (Exception e) { }
        controller.consumer_statistics = Context.getStatistics();
        if ( JedisUtil.isInited() ) {
            int[] conns = JedisUtil.getPools();
            controller.redis_conn = "" + conns[0] + ", " + conns[1];
        } else
            controller.redis_conn = "--, --";
        controller.register = Context.getRegister();
        return controller;
    }


    /** 正在处理的请求 */
    public static class Deal {
        public long     time_all;       // 总用时
        public long     time_deal;      // 处理用时
        public String   filter;         // 过滤器
        public String   service;        // 服务名字
        public String   entry;          // 接口名字
        public int      interceptor;    // 拦截器
        public int      timeout;        // 超时设置
        public String   client;         // 客户端名字/IP
    }

    /** 接口的返回值 */
    public static class Result {
        public String   type;           // 类型
        public String   tips;           // 说明
    }
    /** 接口的参数 */
    public static class Param extends Result {
        public String   name;           // 名字
    }
    /** 接口定义 */
    public static class EntryBase {
        public String   name;           // 接口名字
        public String   tips;           // 接口说明
        public List<Param> params;      // 参数列表
        public Result   result;         // 返回值
        public boolean  readonly;       // 是否读接口
        public int      timeout;        // 超时设置

        public void load(Method method, USEntry use) {
            name = method.getName();
            tips = use.tips();
            params = new ArrayList<>();
            Parameter[] jParams = method.getParameters();
            USParam[] usParams = use.params();
            for ( int i = 1; i < jParams.length; i ++ ) {
                Info.Param param = new Info.Param();
                if ( i <= usParams.length ) {
                    param.name = usParams[i-1].name();
                    param.tips = usParams[i-1].tips();
                } else {
                    param.name = jParams[i].getName();
                    param.tips = "";
                }
                param.type = jParams[i].getParameterizedType().getTypeName();
                params.add(param);
            }
            String res_type = method.getGenericReturnType().getTypeName();
            if ( "void".equals(res_type) )
                result = null;
            else {
                result = new Info.Result();
                result.tips = use.result();
                result.type = res_type;
            }
            readonly = use.readonly();
            timeout = use.timeout();
        }
    }
    /** 接口定义 */
    public static class Entry extends EntryBase {
        public int      dealing;        // 正在处理的请求数量
        public long     deal_over;      // 处理完成的数量
        public long     deal_error;     // 错误的数量
        public long     max_time;       // 最长处理时间
        public String   req_id;         // 最长处理时间的请求ID
    }

    /** 服务依赖 */
    public static class Depend {
        public String   version_min;    // 最小版本号
        public String   version_max;    // 最大版本号
        public int      release = -2;   // 发行状态
    }

    /** 微服务注册信息 */
    public static class ForwardService {
        public String service;          // 服务名字
        public String version;          // 版本
        public boolean release;         // 是否release版本
    }

    /** 容器当前的运行参数 */
    public static class Container {
        public String   host = Bootstrap.Host;                  // 主机名字
        public int      port = Bootstrap.Port;                  // 端口号
        public int      backlog = Bootstrap.BackLog;            // socket连接请求的等待队列长度
        public int      io_threads = Bootstrap.IOThreads;       // I/O线程的数量，0表示"CPU内核数 * 2"
        public int      work_threads = Bootstrap.WorkThreads;   // 工作线程的数量
        public int      timeout_fuse = Bootstrap.TimeoutFuse;   // 当前接口超时的数量达到多少个后熔断，0表示不熔断
        public int      overload = Bootstrap.Overload;          // 请求等待队列的最大长度
        public int      forward = Bootstrap.Forward;            // 转发请求时的等待超时时间（秒数），0表示不转发
        public List<ForwardService> forward_door = Bootstrap.ForwardDoor;   // 需注册的"转发"微服务
    }

    /** 容器运行参数的说明 */
    public static class ContainerComment {
        public String   host = "主机名字（重启生效）";
        public String   port = "端口号（重启生效）";
        public String   backlog = "socket连接请求的等待队列长度（重启生效）";
        public String   io_threads = "I/O线程的数量，0表示\"CPU内核数 * 2\"（重启生效）";
        public String   work_threads = "工作线程的数量（重启生效）";
        public String   timeout_fuse = "当前接口超时的数量达到多少个后熔断，0表示不熔断";
        public String   overload = "请求等待队列的最大长度";
        public String   forward = "转发请求时的等待超时时间（秒数），0表示不转发";
        public String   forward_door = "需注册的\"转发\"微服务，格式：[ { 'service':'xxx', 'version':'1.0.0', 'release':true }, ... ]";
    }

    /** 所有的运行参数 */
    public static class AllConfig extends Config {
        public Container        container;          // 当前的Container参数
        public Container        container_restart;  // 重启后生效的Container参数
        public ContainerComment container_comment;  // 配置项说明
    }

    /** ACL访问控制项 */
    public static class Acl {
        public String               name;           // 服务名字的前缀
        public String               default_auth;   // 指定服务的缺省权限
        public Map<String, String>  spec_auth;      // 指定主机的访问权限
    }
    /** ACL访问控制表 */
    public static class AclTable {
        public Set<String>  accept_host;            // 允许接入的主机地址
        public String       default_auth;           // 所有服务的缺省权限
        public List<Acl>    services;               // 各个服务的ACL配置项
    }

    /** JAR包的Maven:GAV坐标 */
    public static class GAV {
        public String   groupId;        // Maven:组ID
        public String   artifactId;     // Maven:产品ID
        public String   version;        // Maven:版本号

        public GAV() {}
        public GAV(String group, String artifact, String ver) {
            groupId = group;
            artifactId = artifact;
            version = ver;
        }
        public int hashCode() {
            return (groupId + "#" + artifactId + "#" + version).hashCode();
        }
        public boolean equals(Object o) {
            if ( o == null || !(o instanceof GAV) )
                return false;
            GAV gav = (GAV)o;
            return this.toString().equals(gav.toString());
        }
        public String toString() {
            return groupId + "#" + artifactId + "#" + version;
        }
        public String getJarFileName() {
            Info.Lib lib = LibManager.ExtLib.get(hashCode());
            if ( lib == null || Util.checkEmpty(lib.jarFile) == null )
                return artifactId + "-" + version + ".jar";
            return lib.jarFile;
        }
    }
    /** 描述一个JAR包 */
    public static class Lib extends GAV {
        public String   jarFile;        // JAR文件名
        public GAV[]    depends;        // 依赖关系
    }
}
