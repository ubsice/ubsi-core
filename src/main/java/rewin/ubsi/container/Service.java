package rewin.ubsi.container;

import rewin.ubsi.annotation.*;
import rewin.ubsi.common.JedisUtil;
import rewin.ubsi.common.LogUtil;
import rewin.ubsi.common.Util;
import rewin.ubsi.consumer.Context;
import rewin.ubsi.consumer.Register;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 微服务定义
 */
class Service extends Filter {

    /** 接口定义 */
    static class Entry {
        Method      JMethod;        // Method实例
        USEntry     JAnnotation;    // 注解
        AtomicLong  RequestDeal = new AtomicLong(0);    // 计数器：请求处理次数
        AtomicLong  RequestOver = new AtomicLong(0);    // 计数器：请求完成次数
        AtomicLong  RequestError = new AtomicLong(0);   // 计数器：处理异常次数
        AtomicLong  RequestTime = new AtomicLong(0);    // 计时器：最长的处理时间（毫秒）
        String      RequestID;                          // 最长处理时间的请求ID
    }

    String          Name;                               // 缺省的服务名字
    Map<String, Entry>  EntryMap = new HashMap<String, Entry>();    // 接口
    boolean         Single;                             // 是否单例服务

    AtomicLong  RequestDeal = new AtomicLong(0);    // 计数器：请求处理次数
    AtomicLong  RequestOver = new AtomicLong(0);    // 计数器：请求完成次数
    AtomicLong  RequestError = new AtomicLong(0);   // 计数器：处理异常次数

    /** 关闭 */
    @Override
    synchronized boolean stop(String name) throws Exception {
        boolean res = super.stop(name);
        if ( res )
            WorkHandler.clearDealing(name);
        return res;
    }

    /** 启动 */
    @Override
    synchronized boolean start(String name) throws Exception {
        if ( ! Single )
            return super.start(name);
        if ( Status != 0 && Status != -2 )
            return false;
        if ( ! JedisUtil.isInited() ) {
            if ( Status == 0 )
                return super.start(name);
            return false;
        }
        if ( ! Singleton.hasActive(name) ) {
            // 抢先启动
            boolean locked = false;
            try {
                locked = Singleton.lockStart(name, true);
                if ( locked )
                    return super.start(name);
            } finally {
                if ( locked )
                    Singleton.lockStart(name, false);
            }
        }
        if ( Status == 0 ) {
            TimeStatus = (int)(System.currentTimeMillis() / 1000);     // 状态时间
            Status = -2;
            return true;
        }
        return false;
    }

    /** 加载@UService类 */
    static Service load(String className, Info.GAV gav, String serviceName) throws Exception {
        Service srv = new Service();
        if ( gav == null )
            srv.JClass = Class.forName(className);
        else {
            File classPath = serviceName == null ? null : new File(Bootstrap.ServicePath + File.separator + Bootstrap.MODULE_PATH + File.separator + serviceName);
            srv.JClass = LibManager.getClassLoader(gav, classPath).loadClass(className);
        }
        try {
            if ( ! srv.JClass.isAnnotationPresent(UService.class) )
                throw new Exception(className + " is not a UBSI Service");
            srv.JClass.newInstance();     // 测试是否能正常生成对象
            srv.JarLib = gav;
            UService us = (UService)srv.JClass.getAnnotation(UService.class);
            srv.Name = us.name();
            srv.Tips = us.tips();
            srv.Version = Util.getVersion(us.version());
            srv.Release = us.release();
            srv.Single = us.singleton();
            srv.Dependency = srv.loadDepend(us.depend());
            for ( Method method : srv.JClass.getMethods() ) {
                if ( ! Modifier.isStatic(method.getModifiers()) ) {
                    String mname = method.getName();    // public且非static的方法名字
                    if (method.isAnnotationPresent(USEntry.class)) {
                        USEntry usEntry = method.getAnnotation(USEntry.class);
                        if ( srv.EntryMap.containsKey(mname) )
                            throw new Exception(srv.JClass.getName() + " has too many " + mname + "()");
                        Entry entry = new Entry();
                        entry.JMethod = method;
                        entry.JAnnotation = usEntry;
                        srv.EntryMap.put(mname, entry);
                    }
                }
                srv.checkMethod(method);
            }
            Filter.checkContainer(us.container());
            Filter.loadSystemLibs(us.syslib());
        } catch (Exception e) {
            if ( gav != null )
                LibManager.putClassLoader(srv.JClass.getClassLoader());
            throw e;
        }
        srv.TimeStatus = (int)(System.currentTimeMillis() / 1000);     // 状态时间
        srv.Status = 0;
        return srv;
    }

    static long     TimestampHeartbeat = 0; // 心跳广播的时间戳
    static long     TimestampRegister = 0;  // 更新注册项的时间戳
    static boolean  FlushRegister = true;   // 是否立即更新注册项

    // Container的定时任务
    static class TimerDealer extends TimerTask {
        public void run() {
            if ( !JedisUtil.isInited() )
                return;
            // 微服务注册
            long t = System.currentTimeMillis();
            if ( FlushRegister || (t - TimestampRegister > Context.REGISTER_TIMEOUT) ) {
                Set<String>[] timeouts = WorkHandler.getTimeoutDeal();
                Register.Container container = new Register.Container();
                container.Gateway = Bootstrap.Forward > 0;
                container.Overload = Bootstrap.Overload;
                container.Waiting = (int) (Bootstrap.RequestTotal.get() - Bootstrap.RequestDeal.get());
                container.Deal = Bootstrap.RequestOver.get();
                container.Timestamp = t;
                for (String sname : Bootstrap.ServiceMap.keySet()) {
                    Service srv = Bootstrap.ServiceMap.get(sname);
                    if (srv == null)
                        continue;
                    Register.Service rs = new Register.Service();
                    rs.ClassName = srv.JClass.getName();
                    rs.Version = srv.Version;
                    rs.Release = srv.Release;
                    rs.Status = srv.Status;
                    rs.Timeout = timeouts[0].contains(sname);
                    rs.Deal = srv.RequestOver.get();
                    container.Services.put(sname, rs);
                }
                if ( Bootstrap.Forward > 0 ) {
                    List<Info.ForwardService> doors = Bootstrap.ForwardDoor;
                    if ( doors != null && !doors.isEmpty() ) {
                        for ( Info.ForwardService door : doors ) {
                            String sname = Util.checkEmpty(door.service);
                            if ( sname == null || container.Services.containsKey(sname) )
                                continue;
                            Register.Service rs = new Register.Service();
                            rs.ClassName = "";      // 表示仅用来转发的微服务
                            rs.Version = Util.getVersion(door.version);
                            rs.Release = door.release;
                            rs.Status = 1;
                            rs.Timeout = false;
                            rs.Deal = Bootstrap.RequestForward.get();   // 所有微服务的转发总数
                            container.Services.put(sname, rs);
                        }
                    }
                }
                if ( !Bootstrap.FilterList.isEmpty() ) {
                    container.Filters = new ArrayList<>();
                    Iterator<Filter> filters = Bootstrap.FilterList.iterator();
                    while (filters.hasNext()) {
                        Filter filter = filters.next();
                        Register.Filter rf = new Register.Filter();
                        rf.ClassName = filter.JClass.getName();
                        rf.Version = filter.Version;
                        rf.Release = filter.Release;
                        rf.Status = filter.Status;
                        rf.Timeout = timeouts[1].contains(rf.ClassName);
                        container.Filters.add(rf);
                    }
                }
                String which = Bootstrap.Host + "#" + Bootstrap.Port;
                try {
                    Context.setRegister(Context.REG_CONTAINER, which, container);
                    if ( FlushRegister ) {
                        JedisUtil.publish(Context.CHANNEL_NOTIFY, which + "|+");
                        TimestampHeartbeat = System.currentTimeMillis();
                    }
                } catch (Exception e) {
                    Bootstrap.log(LogUtil.ERROR, "register", e);
                }
                TimestampRegister = System.currentTimeMillis();
                FlushRegister = false;
            }

            if ( !FlushRegister && (System.currentTimeMillis() - TimestampHeartbeat >= Context.BEATHEART_SEND * 1000)) {
                String notify = Bootstrap.Host + "#" + Bootstrap.Port;
                int waiting = (int)(Bootstrap.RequestTotal.get() - Bootstrap.RequestDeal.get());
                if ( waiting > 0 )
                    notify += "|" + waiting;
                try {
                    JedisUtil.publish(Context.CHANNEL_NOTIFY, notify);
                } catch (Exception e) {
                    Bootstrap.log(LogUtil.ERROR, "heartbeat", e);
                }
                TimestampHeartbeat = System.currentTimeMillis();
            }
        }
    }

}
