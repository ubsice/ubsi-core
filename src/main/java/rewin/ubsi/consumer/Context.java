package rewin.ubsi.consumer;

import io.netty.channel.*;
import org.slf4j.impl.UbsiLogger;
import redis.clients.jedis.Jedis;
import rewin.ubsi.common.*;

import java.io.File;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * UBSI微服务访问客户端
 */
public class Context {

    /** 自定义异常 */
    public static class ResultException extends Exception {
        int Code = ErrorCode.REQUEST;

        public ResultException(String msg) {
            super(msg);
        }
        public ResultException(int code, String msg) {
            this(msg);
            Code = code;
        }
        public String toString() {
            return super.toString() + ", ErrorCode: " + Code;
        }
        public int getCode() {
            return Code;
        }
    }

    /** 异步方式得到请求结果的回调接口 */
    public static interface ResultNotify {
        /**
         * 回调入口，code表示结果代码，0:成功，result表示结果（如果失败则为提示信息）
         * 注：如果需要进行高耗时的操作，应启动另外的任务线程进行处理，以免阻塞异步I/O
         */
        public void callback(int code, Object result);
    }

    /** 请求过滤器 */
    public static interface Filter {
        /** 前置接口，返回：0-正常，-1-拒绝，1-降级（使用Mock数据） */
        public int before(Context ctx);
        /** 后置接口 */
        public void after(Context ctx);
    }

    public final static int BEATHEART_RECV = 10;    // 读取心跳的超时时间
    public final static int BEATHEART_SEND = 3;     // 发送心跳的超时时间

    public final static long REGISTER_TIMER = 30;           // 定时任务的时间间隔
    public final static long REQUEST_TIMEOUT = 1000;        // 检查请求超时的时间间隔
    public final static long REGISTER_TIMEOUT = 30*1000;    // 容器刷新注册信息的时间间隔

    public final static byte FLAG_DISCARD = 0x01;      // 丢弃结果
    public final static byte FLAG_MESSAGE = 0x02;      // 结果通过消息队列返回
    public final static byte FLAG_LOG = (byte)0x80;    // 强制记录日志

    public final static String CHANNEL_NOTIFY = "_ubsi_notify_";
    public final static byte[] REG_CONTAINER = "_ubsi_container_".getBytes();
    public final static byte[] REG_RESTFUL = "_ubsi_restful_".getBytes();

    public final static String HEADER_REQ_PARAMS = "_ubsi_req_params_";     // 请求Header中表示参数的key
    public final static String HEADER_REQ_FORWARD = "_ubsi_req_forward_";   // 请求转发的路径

    final static int MAX_IOTHREADS = 128;
    final static int MIN_IOTHREADS = 0;
    final static int MAX_TOCONNECTION = 15;
    final static int MIN_TOCONNECTION = 0;
    final static int MAX_TOREQUEST = 900;
    final static int MIN_TOREQUEST = 0;
    final static int MAX_TORECONNECT = 1800;
    final static int MIN_TORECONNECT = 180;
    final static int MAX_REDISIDLE = 16;
    final static int MIN_REDISIDLE = 2;
    final static int MAX_REDISCONN = 128;
    final static int MIN_REDISCONN = 16;

    public static boolean LogNoRouting = true;      // 是否输出路由失败日志

    static int          IOThreads = 0;              // I/O读写线程数
    static int          TimeoutConnection = 5;      // 新建Socket连接时的超时时间
    static int          TimeoutRequest = 10;        // 请求的超时时间
    static int          TimeoutReconnect = 600;     // 连接重试的超时时间

    static String       RedisHost = null;           // Redis主机名
    static int          RedisPort = 6379;           // Redis端口号
    static String       RedisMasterName = null;     // Redis哨兵模式的master名字
    static Set<String>  RedisSentinelAddr = null;   // Redis哨兵模式的节点地址
    static String       RedisPassword = null;       // Redis密码
    static int          RedisMaxIdle = 8;           // Redis连接池的最大空闲数量
    static int          RedisMaxConn = 128;         // Redis连接池的最大数量

    static List<Class<? extends Filter>> Filters = new ArrayList<>();   // 请求过滤器的class列表

    /** 获得redis服务的地址：[ "IP", PORT ] */
    public static Object[] getRedisAddress() {
        if ( RedisHost != null )
            return new Object[] { RedisHost, RedisPort };
        if ( RedisSentinelAddr != null && !RedisSentinelAddr.isEmpty() ) {
            String addr = RedisSentinelAddr.iterator().next();
            String[] arr = addr.split(":");
            return new Object[] { arr[0], Integer.valueOf(arr[1]) };
        }
        return null;
    }

    /** 获得UBSI请求的客户端实例 */
    public static Context request(String service, Object... entryAndParams) throws Exception {
        if ( service == null || entryAndParams == null || entryAndParams.length == 0 || entryAndParams[0] == null ||
                !(entryAndParams[0] instanceof String) || ((String)entryAndParams[0]).length() == 0 )
            throw new ResultException(ErrorCode.REQUEST, "invalid arguments");
        Context context = new Context();
        context.ReqID = Util.getUUID();
        context.Service = service;
        context.Param = entryAndParams;
        context.LogAccess = context.isForceLog();
        List<Class<? extends Filter>> filters = Filters;
        context.FilterInstances = new Filter[filters.size()];
        for ( int i = 0; i < context.FilterInstances.length; i ++ )
            context.FilterInstances[i] = filters.get(i).newInstance();
        return context;
    }

    static String ContextPath = ".";
    final static String MOCK_PATH = "rewin.ubsi.mocks";
    final static String CONFIG_FILE = "rewin.ubsi.consumer.json";
    final static String ROUTER_FILE = "rewin.ubsi.router.json";
    public final static String LOG_PATH = "rewin.ubsi.logs";
    public final static String LOG_FILE = "rewin.ubsi.log.json";

    /* 获得本地数据文件 */
    static File getLocalFile(String filename) {
        return new File(ContextPath + File.separator + filename);
    }
    /* 获得Mock数据文件 */
    static File getMockFile(String service, String entry) {
        return getLocalFile(MOCK_PATH + File.separator + service + File.separator + entry + ".xml");
    }
    /** 设置接口仿真数据 */
    public static void setMockData(String service, String entry, Object data) throws Exception {
        File file = getMockFile(service, entry);
        Util.saveXmlFile(file, data);
    }
    /** 获得接口仿真数据 */
    public static Object getMockData(String service, String entry) throws Exception {
        File file = getMockFile(service, entry);
        return Util.readXmlFile(file);
    }
    /** 获得有仿真数据的接口，service为null表示所有服务 */
    public static Map<String, Set<String>> getMockedEntry(String service) {
        Map<String, Set<String>> res = new HashMap<>();
        if ( service == null ) {
            File[] files = getLocalFile(MOCK_PATH).listFiles();
            if ( files != null )
                for ( File file : files )
                    if ( file.isDirectory() )
                        res.put(file.getName(), getMockedEntry(file));
        } else {
            service = service.trim();
            if ( !service.isEmpty() ) {
                File file = getLocalFile(MOCK_PATH + File.separator + service);
                if ( file.isDirectory() )
                    res.put(service, getMockedEntry(file));
            }
        }
        return res;
    }
    /* 获得有仿真数据的接口 */
    static Set<String> getMockedEntry(File dir) {
        Set<String> res = new HashSet<>();
        File[] files = dir.listFiles();
        if ( files != null ) {
            for (File file : files) {
                if (file.isFile()) {
                    String fname = file.getName();
                    int index = fname.lastIndexOf(".xml");
                    if (index > 0 && index == fname.length() - 4)
                        res.add(fname.substring(0, index));
                }
            }
        }
        return res;
    }
    /** 清除仿真数据，null表示所有 */
    public static void clearMockData(String service, String entry) throws Exception {
        if ( service == null )
            Util.rmdir(getLocalFile(MOCK_PATH));
        else if ( entry == null )
            Util.rmdir(getLocalFile(MOCK_PATH + File.separator + service));
        else
            Util.rmdir(getMockFile(service, entry));
    }

    static long timeHeartbeat = 0;              // 上次心跳时间
    public static long timeHeartbeatMax = 0;    // 最大心跳间隔
    public static long timeHeartbeatMaxAt = 0;  // 最大心跳间隔的发生时间
    static void setTimeHeartbeat() {
        long t = timeHeartbeat;
        timeHeartbeat = System.currentTimeMillis();
        if ( t > 0 && timeHeartbeat - t >= timeHeartbeatMax ) {
            timeHeartbeatMax = timeHeartbeat - t;
            timeHeartbeatMaxAt = timeHeartbeat;
        }
    }

    static long                 JedisTimestamp = 0;     // JedisUtil初始化的时间戳
    static JedisUtil.Listener   JedisListener = null;   // Redis消息监听

    /* 初始化JetisUtil */
    static void initJedis() {
        if ( System.currentTimeMillis() - JedisTimestamp < TimeoutReconnect * 1000 )
            return;
        try {
            if (RedisMasterName != null && RedisSentinelAddr != null)
                JedisUtil.init(RedisMasterName, RedisSentinelAddr, RedisPassword, RedisMaxIdle, RedisMaxConn);
            else if (RedisHost != null)
                JedisUtil.init(RedisHost, RedisPort, RedisPassword, RedisMaxIdle, RedisMaxConn);
            else
                return;

            Router.loadRegister();  // 加载服务注册表

            if ( JedisListener == null ) {
                JedisListener = new JedisUtil.Listener() {
                    @Override
                    public void onMessage(String channel, Object message) {
                        try {
                            if (message == null)
                                return;
                            if (message instanceof String) {
                                Router.Heartbeats.offer((String) message);
                                setTimeHeartbeat();
                            } else if (message instanceof Object[])
                                Connector.setMessageResponse(message);
                        } catch (Exception e) {
                            log(LogUtil.ERROR, "message", e);
                        }
                    }
                    @Override
                    public void onEvent(String channel, Object event) {
                    }
                };
                JedisListener.subscribe(CHANNEL_NOTIFY);
            }
        } catch (Exception e) {
            JedisUtil.close();
            log(LogUtil.ERROR, "redis", e);
        }
        JedisTimestamp = System.currentTimeMillis();
    }

    /** 启动UBSI客户端 */
    public static void startup(String workPath) throws Exception {
        ContextPath = new File(workPath).getCanonicalPath();

        // 读取配置参数
        Config.Consumer config = Util.readJsonFile(getLocalFile(CONFIG_FILE), Config.Consumer.class);
        if ( config != null ) {
            IOThreads = config.io_threads;
            TimeoutConnection = config.timeout_connect;
            TimeoutRequest = config.timeout_request;
            TimeoutReconnect = config.timeout_reconnect;
            RedisHost = config.redis_host;
            RedisPort = config.redis_port;
            RedisMasterName = config.redis_master_name;
            RedisSentinelAddr = config.redis_sentinel_addr;
            RedisPassword = config.redis_password;
            RedisMaxIdle = config.redis_conn_idle;
            RedisMaxConn = config.redis_conn_max;

            if ( config.filters != null ) {
                for ( int i = 0; i < config.filters.size(); i ++ ) {
                    String cname = config.filters.get(i);
                    Class cls = Class.forName(cname);
                    if ( !Filter.class.isAssignableFrom(cls) )
                        throw new Exception("class <" + cname + "> is not a Consumer-Filter");
                    Filters.add(cls);
                }
            }
        }

        // 读取本地路由表
        Register.Router[] routers = Util.readJsonFile(getLocalFile(ROUTER_FILE), Register.Router[].class);
        enableRouter(routers);

        LogUtil.start();
        LogUtil.File_Path = ContextPath + File.separator + LOG_PATH;

        // 读取本地日志参数
        Config.Log logs = Util.readJsonFile(getLocalFile(LOG_FILE), Config.Log.class);
        enableLogConfig(logs);

        JedisTimestamp = 0;
        initJedis();
        IOHandler.init();
        Statistics.Records.clear();     // 清除请求统计数据

        if ( JedisUtil.isInited() )
            try { Thread.sleep(BEATHEART_SEND * 1000); } catch (Exception e) {}
    }
    /** 关闭UBSI客户端 */
    public static void shutdown() {
        Filters.clear();
        IOHandler.close();
        JedisUtil.close();
        LogUtil.stop();
    }

    /** 获得参数配置 */
    public static Config getConfig() throws Exception {
        Config res = new Config();
        res.consumer = new Config.Consumer();
        List<Class<? extends Filter>> filters = Filters;
        if ( !filters.isEmpty() ) {
            res.consumer.filters = new ArrayList<>();
            for ( int i = 0; i < filters.size(); i ++ )
                res.consumer.filters.add(filters.get(i).getName());
        }
        res.consumer.redis_host = RedisHost;
        res.consumer.redis_master_name = RedisMasterName;
        res.consumer.redis_sentinel_addr = RedisSentinelAddr;
        res.consumer_restart = Util.readJsonFile(getLocalFile(CONFIG_FILE), Config.Consumer.class);
        if ( res.consumer != null && res.consumer.redis_password != null )
            res.consumer.redis_password = "********";
        if ( res.consumer_restart != null && res.consumer_restart.redis_password != null )
            res.consumer_restart.redis_password = "********";
        res.consumer_comment = new Config.ConsumerComment();
        return res;
    }
    /* 检查配置项的值 */
    static void checkConfig(Config.Consumer config) throws Exception {
        if ( config.redis_host != null && config.redis_host.isEmpty() )
            config.redis_host = null;
        if ( config.redis_port <= 1000 && config.redis_port >= 65536 )
            throw new Exception("redis port is invalid");
        if ( config.redis_master_name != null && config.redis_master_name.isEmpty() )
            config.redis_master_name = null;
        if ( config.redis_sentinel_addr != null && config.redis_sentinel_addr.isEmpty() )
            config.redis_sentinel_addr = null;
        if ( config.redis_password != null && config.redis_password.isEmpty() )
            config.redis_password = null;
        config.redis_conn_idle = Util.checkMinMax(config.redis_conn_idle, MIN_REDISIDLE, MAX_REDISIDLE);
        config.redis_conn_max = Util.checkMinMax(config.redis_conn_idle, MIN_REDISCONN, MAX_REDISCONN);
        config.io_threads = Util.checkMinMax(config.io_threads, MIN_IOTHREADS, MAX_IOTHREADS);
        config.timeout_connect = Util.checkMinMax(config.timeout_connect, MIN_TOCONNECTION, MAX_TOCONNECTION);
        config.timeout_request = Util.checkMinMax(config.timeout_request, MIN_TOREQUEST, MAX_TOREQUEST);
        config.timeout_reconnect = Util.checkMinMax(config.timeout_reconnect, MIN_TORECONNECT, MAX_TORECONNECT);
    }
    /** 动态更新配置 */
    public static void setConfig(Config.Consumer config) throws Exception {
        if ( config.filters != null ) {
            List<Class<? extends Filter>> filters = new ArrayList<>();
            for ( int i = 0; i < config.filters.size(); i ++ ) {
                String cname = config.filters.get(i);
                Class cls = Class.forName(cname);
                if ( !Filter.class.isAssignableFrom(cls) )
                    throw new Exception("class <" + cname + "> is not a Consumer-Filter");
                filters.add(cls);
            }
            Filters = filters;
        }
        checkConfig(config);
        Util.saveJsonFile(getLocalFile(CONFIG_FILE), config);
        TimeoutConnection = config.timeout_connect;
        TimeoutRequest = config.timeout_request;
        TimeoutReconnect = config.timeout_reconnect;
    }

    /** 获得本地路由表 */
    public static Register.Router[] getRouteTable() {
        return Router.LocalTable;
    }
    /* 路由配置项生效 */
    static void enableRouter(Register.Router[] router) {
        if ( router != null && router.length > 1 )
            Arrays.sort(router, new Comparator<Register.Router>() {
                @Override
                public int compare(Register.Router o1, Register.Router o2) {
                    int len1 = o1.Service.length();
                    int len2 = o2.Service.length();
                    if ( len1 > len2 )
                        return -1;
                    else if ( len1 < len2 )
                        return 1;
                    len1 = o1.Entry == null ? 0 : o1.Entry.length();
                    len2 = o2.Entry == null ? 0 : o2.Entry.length();
                    if ( len1 > len2 )
                        return -1;
                    else if ( len1 < len2 )
                        return 1;
                    return 0;
                }
            });
        Router.LocalTable = router;
    }
    /** 设置本地路由表 */
    public static void setRouteTable(Register.Router[] router) throws Exception {
        Util.saveJsonFile(getLocalFile(ROUTER_FILE), router);
        enableRouter(router);
    }
    /** 获得服务注册表 */
    public static Map<String, Register.Container> getRegister() {
        return Router.Containers;
    }
    /** 获得指定的注册表 */
    public static <T> Map<String, T> getRegister(byte[] reg_key, Type type, Type... typeArguments) throws Exception {
        try (Jedis jedis = JedisUtil.getJedis()) {
            Map<byte[], byte[]> map = jedis.hgetAll(reg_key);
            Map<String, T> res = new HashMap<String, T>();
            for ( Map.Entry<byte[], byte[]> entry : map.entrySet() ) {
                Object obj = Codec.decodeBytes(entry.getValue());
                res.put(new String(entry.getKey()), type.getClass().isInstance(obj) ? (T)obj : Codec.toType(obj, type, typeArguments));
            }
            return res;
        }
    }
    /** 设置指定的注册表 */
    public static void setRegister(byte[] reg_key, String key, Object value) throws Exception {
        try (Jedis jedis = JedisUtil.getJedis()) {
            jedis.hset(reg_key, key.getBytes(), Codec.encodeBytes(value));
        }
    }
    /** 删除指定的注册表项 */
    public static void delRegister(byte[] reg_key, String key) throws Exception {
        try (Jedis jedis = JedisUtil.getJedis()) {
            jedis.hdel(reg_key, key.getBytes());
        }
    }

    public final static String  LOG_APPID = "rewin.ubsi.consumer";
    static String               LogAppTag = null;       // 日志的应用分类
    static Config.LogAccess[]   LogForce = null;        // 强制记录consumer请求日志的服务列表

    /** 设置Consumer日志行为的APP属性 */
    public static void setLogApp(String appAddr, String appTag) {
        LogUtil.App_Addr = appAddr;
        LogAppTag = appTag;
    }
    /** 获得Log参数 */
    public static Config.Log getLogConfig() {
        Config.Log log = new Config.Log();
        log.options = new Config.LogOpt();
        Config.LogOpt opt = log.options;
        opt.all = new Config.LogItem();
        opt.all.output = LogUtil.Default_Output;
        opt.all.filename = LogUtil.Default_File;
        if ( LogUtil.Debug_Output >= 0 || LogUtil.Debug_File != null ) {
            opt.debug = new Config.LogItem();
            opt.debug.output = LogUtil.Debug_Output;
            opt.debug.filename = LogUtil.Debug_File;
        }
        if ( LogUtil.Info_Output >= 0 || LogUtil.Info_File != null ) {
            opt.info = new Config.LogItem();
            opt.info.output = LogUtil.Info_Output;
            opt.info.filename = LogUtil.Info_File;
        }
        if ( LogUtil.Warn_Output >= 0 || LogUtil.Warn_File != null ) {
            opt.warn = new Config.LogItem();
            opt.warn.output = LogUtil.Warn_Output;
            opt.warn.filename = LogUtil.Warn_File;
        }
        if ( LogUtil.Error_Output >= 0 || LogUtil.Error_File != null ) {
            opt.error = new Config.LogItem();
            opt.error.output = LogUtil.Error_Output;
            opt.error.filename = LogUtil.Error_File;
        }
        if ( LogUtil.Action_Output >= 0 || LogUtil.Action_File != null ) {
            opt.action = new Config.LogItem();
            opt.action.output = LogUtil.Action_Output;
            opt.action.filename = LogUtil.Action_File;
        }
        if ( LogUtil.Access_Output >= 0 || LogUtil.Access_File != null ) {
            opt.access = new Config.LogItem();
            opt.access.output = LogUtil.Access_Output;
            opt.access.filename = LogUtil.Access_File;
        }
        if ( LogUtil.App_Output >= 0 || LogUtil.App_File != null ) {
            opt.app = new Config.LogItem();
            opt.app.output = LogUtil.App_Output;
            opt.app.filename = LogUtil.App_File;
        }
        log.consumer = LogForce;
        log.slf4j_level = UbsiLogger.getLogLevel();
        log.js_level = ScriptUtil.getLogLevel();
        return log;
    }
    /* Log配置项生效 */
    static void enableLogConfig(Config.Log log) {
        if ( log == null )
            return;
        if ( log.options != null ) {
            Config.LogOpt opt = log.options;
            if ( opt.all != null ) {
                if ( opt.all.output >= 0 )
                    LogUtil.Default_Output = opt.all.output;
                if ( opt.all.filename != null )
                    LogUtil.Default_File = opt.all.filename;
            }
            LogUtil.Debug_Output = opt.debug == null ? -1 : opt.debug.output;
            LogUtil.Debug_File = opt.debug == null ? null : opt.debug.filename;
            LogUtil.Info_Output = opt.info == null ? -1 : opt.info.output;
            LogUtil.Info_File = opt.info == null ? null : opt.info.filename;
            LogUtil.Warn_Output = opt.warn == null ? -1 : opt.warn.output;
            LogUtil.Warn_File = opt.warn == null ? null : opt.warn.filename;
            LogUtil.Error_Output = opt.error == null ? -1 : opt.error.output;
            LogUtil.Error_File = opt.error == null ? null : opt.error.filename;
            LogUtil.Action_Output = opt.action == null ? -1 : opt.action.output;
            LogUtil.Action_File = opt.action == null ? null : opt.action.filename;
            LogUtil.Access_Output = opt.access == null ? -1 : opt.access.output;
            LogUtil.Access_File = opt.access == null ? null : opt.access.filename;
            LogUtil.App_Output = opt.app == null ? -1 : opt.app.output;
            LogUtil.App_File = opt.app == null ? null : opt.app.filename;
        }
        LogForce = log.consumer;
        UbsiLogger.setLogLevel(log.slf4j_level);
        ScriptUtil.setLogLevel(log.js_level);
    }
    /** 设置Log参数 */
    public static void setLogConfig(Config.Log log) throws Exception {
        Util.saveJsonFile(getLocalFile(LOG_FILE), log);
        enableLogConfig(log);
    }
    /** 获得日志文件列表 */
    public static List<Config.LogFile> getLogFileList(String dir) {
        String path = LOG_PATH;
        if ( dir != null && !dir.trim().isEmpty() )
            path = path + File.separator + Config.repaireDir(dir);
        File fd = getLocalFile(path);
        return Config.getFileList(fd);
    }
    /** 获得日志文件内容 */
    public static Object[] getLogFile(String dir, String filename, long offset, int length) throws Exception {
        String path = LOG_PATH;
        if ( dir != null && !dir.trim().isEmpty() )
            path = path + File.separator + Config.repaireDir(dir);
        return Config.readFile(getLocalFile(path + File.separator + filename), offset, length);
    }
    /* 输出1条日志 */
    static void log(int type, String tips, Object body) {
        LogUtil.log(type, LogAppTag, LOG_APPID, new Throwable(), 1, tips, body);
    }
    /* 输出1条日志 */
    static void log(int type, int callStack, String tips, Object body) {
        LogUtil.log(type, LogAppTag, LOG_APPID, new Throwable(), 1 + callStack, tips, body);
    }
    /** 获得日志记录器 */
    public static Logger getLogger(String appTag, String appID) {
        return new Logger(appTag, appID);
    }

    /** 得到统计数据 */
    public static Register.Statistics getStatistics(String service, String entry) {
        ConcurrentMap<String, Statistics> map = Statistics.Records.get(service);
        if ( map == null )
            return null;
        Statistics statistics = map.get(entry);
        if ( statistics == null )
            return null;
        Register.Statistics res_entry = new Register.Statistics();
        res_entry.request = statistics.request.get();
        res_entry.result = statistics.result.get();
        res_entry.success = statistics.success.get();
        res_entry.max_time = statistics.max_time.get();
        res_entry.req_id = statistics.request_id;
        return res_entry;
    }
    /** 得到统计数据 */
    public static Map<String, Register.Statistics> getStatistics(String service) {
        ConcurrentMap<String, Statistics> map = Statistics.Records.get(service);
        Map<String, Register.Statistics> res_srv = new HashMap<>();
        if ( map == null )
            return null;
        for ( String entry : map.keySet() )
            res_srv.put(entry, getStatistics(service, entry));
        return res_srv;
    }
    /** 得到统计数据 */
    public static Map<String, Map<String, Register.Statistics>> getStatistics() {
        Map<String, Map<String, Register.Statistics>> res = new HashMap<>();
        for ( String service : Statistics.Records.keySet() )
            res.put(service, getStatistics(service));
        return res;
    }

    //////////////////////////////////////////////////////////////////

    String      ReqID;          // 请求ID
    String      SeqID = null;   // 前置请求的ID
    Map<String,Object> Header;  // 请求头
    String      Service;        // 服务名字
    Object[]    Param;          // 接口参数

    int         VerMin = 0;         // 最小版本号
    int         VerMax = 0;         // 最大版本号
    int         VerRelease = -1;    // 是否正式版本

    int Timeout = TimeoutRequest;   // 超时时间
    boolean ConnectAlone = false;   // 是否单独连接
    boolean LogAccess = false;      // 是否强制记录Access日志

    Filter[]    FilterInstances = null;
    String      TargetContainer = null;     // 目标容器
    Channel     TargetChannel = null;       // 目标连接

    ResultNotify    Notify = null;              // 收到结果的回调
    long            RequestTime = 0;            // 发出请求的时间戳
    long            ResultTime = 0;             // 处理结果的时间戳
    boolean         ResultStatus = false;       // 是否已经结束
    int             ResultCode = ErrorCode.TIMEOUT;   // 结果代码
    Object          ResultData = null;          // 结果数据
    Map<String,Object> Tailer;  // 结果的附加数据

    /* 处理请求过滤器的前置动作 */
    boolean doBefore() {
        if ( FilterInstances == null )
            return false;
        for ( int i = 0; i < FilterInstances.length; i ++ ) {
            if ( ResultStatus ) {
                FilterInstances[i] = null;
                continue;
            }

            try {
                int res = FilterInstances[i].before(this);
                if ( res < 0 )
                    setResult(ErrorCode.FILTER, "break by filter <" + FilterInstances[i].getClass().getCanonicalName() + ">");
                else if ( res > 0 )
                    setResult(ErrorCode.OK, Context.getMockData(Service, (String)Param[0]));
            } catch (Exception e) {
                setResult(ErrorCode.FILTER, "filter <" + FilterInstances[i].getClass().getCanonicalName() + "> error: " + e.getMessage());
            }
        }
        return ResultStatus;
    }
    /* 处理请求过滤器的后置动作 */
    void doAfter() {
        if ( FilterInstances != null )
            for ( int i = FilterInstances.length - 1; i >= 0; i -- )
                if ( FilterInstances[i] != null )
                    try { FilterInstances[i].after(this); } catch (Exception e) { log(LogUtil.ERROR, Service + "#" + Param[0] + "() after", e); }
    }

    /* 发送UBSI请求 */
    boolean sendRequest(Channel ch, boolean discard, boolean message) {
        byte flag = 0;
        if ( discard )
            flag |= FLAG_DISCARD;
        else if ( message )
            flag |= FLAG_MESSAGE;
        if ( LogAccess ) {
            if ( LogUtil.LOG_SERVICE.equals(Service))
                LogAccess = false;
            else
                flag |= FLAG_LOG;
        }

        if ( RequestTime != 0 ) {
            setResult(ErrorCode.REPEAT, "discard request");
            return true;
        }
        RequestTime = System.currentTimeMillis();
        ResultTime = RequestTime;
        ResultStatus = false;

        TargetChannel = ch;
        if ( doBefore() ) {
            doAfter();
            return true;
        }

        if ( IOData.write(ch, new Object[] { ReqID, Header, Service, Param, flag }) ) {
            Statistics.send(Service, (String)Param[0]);
            if (LogAccess)
                log(LogUtil.ACCESS, 2, "request", new LogBody.Request(ReqID, SeqID, Service, (String) Param[0], flag));
            return false;
        }
        setResult(ErrorCode.CHANNEL, "send request error");
        return true;
    }
    /* 记录Access结果日志 */
    void logResult() {
        doAfter();
        Statistics.recv(Service, (String)Param[0], ResultCode, ResultTime - RequestTime, ReqID);    // 返回统计
        if ( LogAccess )
            log(LogUtil.ACCESS, 2, "result", new LogBody.Result(ReqID, Service, (String) Param[0], ResultCode, ResultData, ResultTime - RequestTime));
    }
    /* 回调UBSI结果 */
    void resultCallback() {
        try {
            Notify.callback(ResultCode, ResultData);
        } catch (Exception e) {
            log(LogUtil.ERROR, Service + "#" + Param[0] + "() notify", e);
        }
    }
    /* 是否需要强制记录Access日志 */
    boolean isForceLog() {
        Config.LogAccess[] logForce = LogForce;
        if ( logForce == null || logForce.length == 0 )
            return false;
        for ( Config.LogAccess la : logForce )
            if ( Util.matchString(Service, la.service) && Util.matchString((String)Param[0], la.entry) )
                return true;
        return false;
    }

    /** 获得请求ID */
    public String getReqID() {
        return ReqID;
    }
    /** 获得服务名字 */
    public String getService() {
        return Service;
    }
    /** 获得接口名字 */
    public String getEntry() {
        return (String)Param[0];
    }
    /** 获得参数数量 */
    public int getParamCount() {
        return Param.length - 1;
    }
    /** 获得参数 */
    public Object getParam(int index) {
        return Param[index + 1];
    }
    /** 设置参数 */
    public void setParam(Object... o) {
        Object[] params = new Object[o.length + 1];
        params[0] = Param[0];
        for ( int i = 0; i < o.length; i ++ )
            params[i + 1] = o[i];
        Param = params;
    }
    /** 设置前置请求ID（会激活强制记录ACCESS日志的机制） */
    public Context setSeqID(String seqID) {
        SeqID = seqID;
        if ( SeqID != null )
            LogAccess = true;
        return this;
    }
    /** 设置是否强制记录ACCESS日志 */
    public Context setLogAccess(boolean force) {
        LogAccess = force;
        return this;
    }
    /** 是否强制记录ACCESS日志 */
    public boolean isLogAccess() {
        return LogAccess;
    }
    /** 设置Header数据项 */
    public Context setHeader(String key, Object value) {
        if ( Header == null )
            Header = new HashMap<String,Object>();
        Header.put(key, value);
        return this;
    }
    /** 设置Header数据对象 */
    public Context setHeader(Map<String,Object> header) {
        Header = header;
        return this;
    }
    /** 获取Header数据项 */
    public Object getHeader(String key) {
        return Header == null ? null : Header.get(key);
    }
    /** 获取Header数据 */
    public Map<String,Object> getHeader() {
        return Header;
    }
    /** 设置Tailer数据项 */
    public Context setTailer(String key, Object value) {
        if ( Tailer == null )
            Tailer = new HashMap<>();
        Tailer.put(key, value);
        return this;
    }
    /** 设置Tailer数据对象 */
    public Context setTailer(Map<String,Object> tailer) {
        Tailer = tailer;
        return this;
    }
    /** 获取Tailer数据项 */
    public Object getTailer(String key) {
        return Tailer == null ? null : Tailer.get(key);
    }
    /** 获取Tailer数据对象 */
    public Map<String,Object> getTailer() {
        return Tailer;
    }
    /** 设置版本信息的过滤，参数值为-1表示忽略此参数，缺省的min/max为0/0 */
    public Context setVersion(int min, int max) {
        if ( min >= 0 )
            VerMin = min;
        if ( max >= 0 )
            VerMax = max;
        return this;
    }
    /** 设置版本信息的过滤，min/max为-1表示忽略此参数，release为0表示必须非release/1表示必须release/-1表示不限，缺省为-1 */
    public Context setVersion(int min, int max, int release) {
        setVersion(min, max);
        VerRelease = release;
        return this;
    }
    /** 设置请求超时时间(秒数)，0表示不限 */
    public Context setTimeout(int timeout) {
        Timeout = timeout;
        return this;
    }
    /** 获取请求的超时时间(秒数)，0表示不限 */
    public int getTimeout() {
        return Timeout;
    }
    /** 设置是否使用独立连接发送请求（适用于较大数据量传输的请求），缺省为false */
    public Context setConnectAlone(boolean alone) {
        ConnectAlone = alone;
        return this;
    }
    /** 是否使用独立连接发送请求 */
    public boolean isConnectAlone() {
        return ConnectAlone;
    }
    /** 获得路由结果，成功返回["host",port]，返回[data]表示仿真数据 */
    public Object[] getRouter() throws Exception {
        Object[] res = Router.getServer(Service, (String)Param[0], VerMin, VerMax, VerRelease);
        if ( res.length > 1 )
            TargetContainer = (String)res[0] + "#" + res[1];
        return res;
    }
    /** 获得请求的目标容器 */
    public String getTargetContainer() {
        return TargetContainer;
    }
    /** 获得请求的目标连接 */
    public Channel getTargetChannel() {
        return TargetChannel;
    }
    /** 获取处理时间（毫秒数，0表示还未发送请求，<0表示请求还未返回） */
    public long getResultTime() {
        if ( ResultStatus ) {
            long t = ResultTime - RequestTime;
            return t == 0 ? 1 : t;
        }
        if ( RequestTime == 0 )
            return 0;
        long t = RequestTime - System.currentTimeMillis();
        return t == 0 ? -1 : t;
    }
    /** 获取结果代码 */
    public int getResultCode() {
        return ResultCode;
    }
    /** 获取结果数据 */
    public Object getResultData() {
        return ResultData;
    }
    /** 设置结果 */
    public void setResult(int code, Object data) {
        ResultStatus = true;
        ResultTime = System.currentTimeMillis();
        ResultCode = code;
        ResultData = data;
    }

    /** 直接向指定的container发送请求（同步） */
    public Object direct(String host, int port) throws Exception {
        ConnectAlone = true;
        TargetContainer = host + "#" + port;
        Channel ch = IOHandler.connect(host, port);
        Notify = null;
        Connector.DirectContext.put(ch, this);
        synchronized (this) {
            if ( sendRequest(ch, false, false) ) {
                Connector.DirectContext.remove(ch);
                ch.close();
                if ( ResultCode != ErrorCode.OK )
                    throw new ResultException(ResultCode, (String)ResultData);
                return ResultData;
            }
            try {
                if ( Timeout == 0 )
                    this.wait();
                else
                    this.wait(Timeout * 1000);
            } catch(Exception e) {}
            if ( !ResultStatus ) {
                setResult(ErrorCode.TIMEOUT, "request timeout");
                logResult();
            }
        }
        Connector.DirectContext.remove(ch);
        ch.close();
        if ( ResultCode != ErrorCode.OK )
            throw new ResultException(ResultCode, (String)ResultData);
        return ResultData;
    }

    /** 直接向指定的container发送请求（异步） */
    public void directAsync(String host, int port, ResultNotify notify, boolean message) throws Exception {
        if ( notify != null && message && !JedisUtil.isInited() )
            throw new ResultException(ErrorCode.MESSAGE, "message mechanism invalid");
        ConnectAlone = true;
        TargetContainer = host + "#" + port;
        Channel ch = IOHandler.connect(host, port);
        if ( notify != null ) {
            Notify = notify;
            if ( message )
                Connector.MessageContext.put(ReqID, this);
            else
                Connector.DirectContext.put(ch, this);
        }
        if ( sendRequest(ch, notify == null, message) ) {
            try { ch.close(); } catch (Exception e) {}
            if ( notify != null ) {
                if ( message )
                    Connector.MessageContext.remove(ReqID);
                else
                    Connector.DirectContext.remove(ch);
                notify.callback(ResultCode, ResultData);
            }
            return;
        }
        if ( notify == null || message )
            ch.close();
    }

    /** 同步方式请求UBSI服务 */
    public Object call() throws Exception {
        Object[] server = getRouter();
        if ( server.length == 1 )
            return server[0];

        if ( ConnectAlone ) {
            try {
                return direct((String) server[0], (Integer) server[1]);
            } catch (ResultException e) {
                if (e.Code != ErrorCode.CONNECT)
                    throw e;
                server = getRouter();
                return direct((String) server[0], (Integer) server[1]);
            }
        }

        Channel ch = null;
        try {
            ch = Connector.get((String)server[0], (Integer)server[1]);
        } catch (ResultException e) {
            if (e.Code != ErrorCode.CONNECT)
                throw e;
            server = getRouter();
            ch = Connector.get((String)server[0], (Integer)server[1]);
        }

        Notify = null;
        Connector.putChannelContext(ch, this, true);
        synchronized (this) {
            if ( sendRequest(ch, false, false) ) {
                Connector.putChannelContext(ch, this, false);
                if ( ResultCode != ErrorCode.OK )
                    throw new ResultException(ResultCode, (String)ResultData);
                return ResultData;
            }
            try {
                if ( Timeout == 0 )
                    this.wait();
                else
                    this.wait(Timeout * 1000);
            } catch(Exception e) {}
            if ( !ResultStatus ) {
                setResult(ErrorCode.TIMEOUT, "request timeout");
                logResult();
            }
        }
        Connector.putChannelContext(ch, this, false);
        if ( ResultCode != ErrorCode.OK )
            throw new ResultException(ResultCode, (String)ResultData);
        return ResultData;
    }

    /** 异步方式请求UBSI服务 */
    public void callAsync(ResultNotify notify, boolean message) throws Exception {
        if ( notify != null && message && !JedisUtil.isInited() )
            throw new ResultException(ErrorCode.MESSAGE, "message mechanism invalid");

        Object[] server = getRouter();
        if ( server.length == 1 ) {
            if ( notify != null )
                notify.callback(ErrorCode.OK, server[0]);
            return;
        }

        if ( ConnectAlone ) {
            try {
                directAsync((String) server[0], (Integer) server[1], notify, message);
            } catch (ResultException e) {
                if (e.Code != ErrorCode.CONNECT)
                    throw e;
                server = getRouter();
                directAsync((String) server[0], (Integer) server[1], notify, message);
            }
            return;
        }

        Channel ch = null;
        try {
            ch = Connector.get((String)server[0], (Integer)server[1]);
        } catch (ResultException e) {
            if (e.Code != ErrorCode.CONNECT)
                throw e;
            server = getRouter();
            ch = Connector.get((String)server[0], (Integer)server[1]);
        }

        if ( notify != null ) {
            Notify = notify;
            if (message)
                Connector.MessageContext.put(ReqID, this);
            else
                Connector.putChannelContext(ch, this, true);
        }
        if ( sendRequest(ch, notify == null, message) ) {
            if ( notify != null ) {
                if (message)
                    Connector.MessageContext.remove(ReqID);
                else
                    Connector.putChannelContext(ch, this, false);
                notify.callback(ResultCode, ResultData);
            }
        }
    }

}
