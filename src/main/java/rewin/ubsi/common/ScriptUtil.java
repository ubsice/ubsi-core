package rewin.ubsi.common;

import com.google.gson.Gson;
import rewin.ubsi.consumer.Context;
import rewin.ubsi.consumer.Logger;
import rewin.ubsi.container.ServiceContext;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

/**
 * JavaScript脚本工具
 */
public class ScriptUtil {

    /** 输出信息 */
    public static class Message {
        public long     time;   // 时间戳，毫秒数
        public int      type;   // 消息类别，LogUtil.DEBUG | INFO | ERROR
        public String   text;   // 消息内容
    }

    /** API说明 */
    public static Map Api = Util.toMap(
            "$.host('host', port)", "设置UBSI请求的目标容器，host为null表示路由模式",
            "$.header({...})", "设置UBSI请求的Header",
            "$.version('min_ver', 'max_ver', release)", "设置UBSI请求的服务版本限制，release取值：-1-不限，0-非release，1-release",
            "$.timeout(seconds)", "设置UBSI请求的超时时间（秒数），0表示不限，-1表示使用缺省值",
            "$.async(true|false)", "设置同步/异步方式发送请求，默认方式为同步",
            "$.request('service', 'entry', ...)", "发送UBSI请求，同步方式返回结果，异步方式直接返回null",
            "$.result(data)", "设置脚本的返回结果，如果不设置，则将最后一条语句的值作为脚本结果",
            "$.sleep(millis)", "暂停millis毫秒",
            "$.json(obj)", "将obj转换为json字符串，JS解析：val=eval('('+{str}+')');",
            "$.debug('msg')", "输出一条debug信息",
            "$.info('msg')", "输出一条info信息",
            "$.error('msg')", "输出一条error信息",
            "$.broadcast('channel', data)", "发送一条广播消息",
            "$.throwEvent('channel', data)", "发送一条事件消息",
            "$._byte(n)", "将JS的number转换为Java的byte",
            "$._int(n)", "将JS的number转换为Java的int",
            "$._long(n)", "将JS的number转换为Java的long",
            "$._bigint('str')", "将JS字符串转换为Java的BigInteger",
            "$._double(n)", "将JS的number转换为Java的double",
            "$._bignum('str')", "将JS字符串转换为Java的BigDecimal",
            "$._bytes([...])", "将JS的number数组转换为Java的byte[]",
            "$._map({...})", "将JS的对象转换为Java的Map",
            "$._list([...])", "将JS数组转换为Java的List（注：Java会将JS的[]当成Map）",
            "$._set([...])", "将JS数组转换为Java的Set",
            "$._array([...])", "将JS数组转换为Java的Object[]",
            "Packages.xxx", "引用Java的类，例如：var n = new Packages.java.lang.Integer(10);"
    );

    private static int logLevel = LogUtil.DEBUG;     // 日志输出级别
    public static void setLogLevel(String level) {
        level = Util.checkEmpty(level);
        if ( level == null )
            return;
        if ( "NONE".equalsIgnoreCase(level) )
            logLevel = LogUtil.ERROR - 1;           // 关闭所有日志输出
        else if ( "ERROR".equalsIgnoreCase(level) )
            logLevel = LogUtil.ERROR;
        else if ( "INFO".equalsIgnoreCase(level) )
            logLevel = LogUtil.INFO;
        else if ( "DEBUG".equalsIgnoreCase(level) )
            logLevel = LogUtil.DEBUG;
    }
    public static String getLogLevel() {
        if ( logLevel >= LogUtil.DEBUG )
            return "DEBUG";
        if ( logLevel >= LogUtil.INFO )
            return "INFO";
        if ( logLevel >= LogUtil.ERROR )
            return "ERROR";
        return "NONE";
    }

    String  Host = null;        // direct模式的主机名字，null表示路由模式
    int     Port = 7112;
    int     VerMin = 0;
    int     VerMax = 0;
    int     VerRel = -1;
    Map     Header = null;
    int     Timeout = -1;       // 缺省的超时时间，0表示不限

    boolean Async = false;          // 是否异步方式
    boolean HasResult = false;      // 是否设置了结果
    public Object  Result = null;   // 脚本的执行结果

    String  LogAppTag = null;
    String  LogAppID = null;
    String  LogTips = null;

    ServiceContext serviceContext = null;
    Logger logger = null;

    public List<Message> Messages = new ArrayList<>();     // 输出的消息

    /** 构造函数 */
    public ScriptUtil() {}
    public ScriptUtil(String host, int port) {
        Host = host;
        Port = port;
    }
    public ScriptUtil(String appTag, String appID, String tips) {
        LogAppTag = appTag;
        LogAppID = appID;
        LogTips = tips;
    }
    public ScriptUtil(ServiceContext ctx) {
        serviceContext = ctx;
    }

    /** 设置请求路径，host为null表示路由模式 */
    public void host(String host, int port) {
        Host = host;
        Port = port;
    }

    /** 设置请求Header */
    public void header(Map header) {
        Header = header;
    }

    /** 设置请求的Version */
    public void version(String min, String max, int release) {
        VerMin = Util.getVersion(min);
        VerMax = Util.getVersion(max);
        VerRel = release;
    }

    /** 设置请求的超时时间（秒） */
    public void timeout(int seconds) {
        Timeout = seconds;
    }

    /** 设置同步/异步方式 */
    public void async(boolean yes) {
        Async = yes;
    }

    /** UBSI请求，返回Java数据对象 */
    public Object request(String service, Object... entryAndParams) throws Exception {
        Context context = serviceContext == null ?
                Context.request(service, entryAndParams) :
                serviceContext.request(service, entryAndParams);
        if ( Header != null )
            context.setHeader(Header);
        context.setVersion(VerMin, VerMax, VerRel);
        if ( Timeout >= 0 )
            context.setTimeout(Timeout);
        if ( Async ) {
            if ( Host != null )
                context.directAsync(Host, Port, null, false);
            else
                context.callAsync(null, false);
            return null;
        }
        return Host != null ? context.direct(Host, Port) : context.call();
    }

    /** 设置脚本结果 */
    public void result(Object data) {
        HasResult = true;
        Result = data;
    }

    /** 暂停 */
    public void sleep(int millis) {
        try { Thread.sleep((long)millis); } catch (Exception e) {}
    }

    /** 输出消息 */
    void output(int type, Object msg) {
        if ( logLevel >= LogUtil.ERROR && type <= logLevel ) {
            if ( LogAppTag != null || LogAppID != null || LogTips != null )
                LogUtil.log(type, LogAppTag, LogAppID, new Throwable(), 2, LogTips, msg);
            else if ( serviceContext != null ) {
                if ( logger == null )
                    logger = serviceContext.getLogger();
                logger.log(type, 2, "jscript", msg);
            }
        }
        Message message = new Message();
        message.time = System.currentTimeMillis();
        message.type = type;
        message.text = json(msg);
        Messages.add(message);
    }
    public void debug(Object msg) { output(LogUtil.DEBUG, msg); }
    public void info(Object msg) { output(LogUtil.INFO, msg); }
    public void error(Object msg) { output(LogUtil.ERROR, msg); }
    /** 将obj转换为json字符串，JavaScript的解析方法：var v = eval('(' + str + ')'); */
    public String json(Object obj) { return new Gson().toJson(obj); }

    /** 发送一条广播消息 */
    public void broadcast(String channel, Object data) throws Exception {
        if ( !JedisUtil.isInited() )
            throw new Exception("redis not ready");
        JedisUtil.publish(channel, data);
    }
    /** 发送一条事件消息 */
    public void throwEvent(String channel, Object data) throws Exception {
        if ( !JedisUtil.isInited() )
            throw new Exception("redis not ready");
        JedisUtil.putEvent(channel, data);
    }

    // --- JavaScript数据转换为Java的数据类型 ---

    /** 将number转换为byte */
    public byte _byte(int i) {
        return (byte)(i & 0xff);
    }
    /** 将number转换为int */
    public int _int(int i) {
        return i;
    }
    /** 将number转换为long */
    public long _long(long i) {
        return i;
    }
    /** 将String转换为BigInteger */
    public BigInteger _bigint(String s) {
        return new BigInteger(s);
    }
    /** 将number转换为double */
    public double _double(double i) {
        return i;
    }
    /** 将String转换为BigDecimal */
    public BigDecimal _bignum(String s) {
        return new BigDecimal(s);
    }
    /** 将[number]转换为byte[] */
    public byte[] _bytes(Map x) {
        byte[] res = new byte[x.size()];
        for ( int i = 0; i < res.length; i ++ )
            res[i] = _byte((int)x.get("" + i));
        return res;
    }
    /** 将{...}转换为Map */
    public Map _map(Map x) {
        Map res = new HashMap();
        res.putAll(x);
        return res;
    }
    /** 将[...]转换为List */
    public List _list(Map x) {
        List res = new ArrayList();
        for ( int i = 0; i < x.size(); i ++ )
            res.add(x.get("" + i));
        return res;
    }
    /** 将[...]转换为Set */
    public Set _set(Map x) {
        Set res = new HashSet();
        for ( int i = 0; i < x.size(); i ++ )
            res.add(x.get("" + i));
        return res;
    }
    /** 将[...]转换为Object[] */
    public Object[] _array(Map x) {
        Object[] res = new Object[x.size()];
        for ( int i = 0; i < res.length; i ++ )
            res[i] = x.get("" + i);
        return res;
    }

    // --- 静态方法 ---

    static ScriptEngineManager EngineManager = new ScriptEngineManager();   // 脚本引擎管理器

    /** 获得JavaScript脚本执行引擎 */
    public static ScriptEngine getEngine(ScriptUtil context, Map<String, Object> var) {
        ScriptEngine engine = EngineManager.getEngineByName("JavaScript");
        if ( context != null )
            engine.put("$", context);
        if ( var != null )
            for ( String key : var.keySet() )
                engine.put(key, var.get(key));
        return engine;
    }

    /**
     * 执行JavaScript脚本
     *      JavaScript的基础数据类型：
     *           null    -> null
     *           1       -> int
     *           1.0     -> double
     *           'abc'   -> String
     *           {...}   -> Map
     *           [...]   -> Map
     *      JavaScript抛出异常：
     *           throw 'js exception';
     * 返回：
     *      JavaScript执行结果
     */
    public static Object runJs(ScriptEngine engine, String js) throws Exception {
        ScriptUtil context = (ScriptUtil)engine.get("$");
        context.HasResult = false;
        try {
            Object res = engine.eval(js);
            if ( !context.HasResult )
                context.Result = res;
        } catch (Exception e) {
            context.Result = e;
            throw e;
        }
        return context.Result;
    }
    public static Object runJs(String js, ScriptUtil context, Map<String, Object> var) throws Exception {
        return runJs(getEngine(context, var), js);
    }
}
