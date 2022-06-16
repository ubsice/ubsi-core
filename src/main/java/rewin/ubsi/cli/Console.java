package rewin.ubsi.cli;

import com.google.gson.Gson;
import redis.clients.jedis.Jedis;
import rewin.ubsi.common.*;
import rewin.ubsi.consumer.Context;
import rewin.ubsi.container.Info;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.*;

/**
 * 命令行工具：访问容器
 */
public class Console {

    static boolean  DMode = true;           // 直连模式
    static String   Host = "localhost";
    static int      Port = 7112;
    static String   UseService = null;      // USE模式
    static boolean  XmlFormat = false;
    static int      Timeout = 10;           // Context.TimeoutRequest
    static boolean  ConnectAlone = false;
    static boolean  AsyncMode = false;
    static boolean  TraceLog = false;
    static Map<String,Object>   Header = new HashMap<>();
    static int      VerMin = 0;
    static int      VerMax = 0;
    static int      VerRel = -1;
    static JedisUtil.Listener JListener = new JedisUtil.Listener() {
        @Override
        public void onMessage(String channel, Object message) throws Exception {
            System.out.print("\n~~~ receive message from [" + channel + "]: ");
            if ( XmlFormat )
                Request.printXml(message);
            else
                Request.printJson(JsonCodec.encodeType(message));
        }
        @Override
        public void onEvent(String channel, Object event) throws Exception {
            System.out.print("\n~~~ receive event from [" + channel + "]: ");
            if ( XmlFormat )
                Request.printXml(event);
            else
                Request.printJson(JsonCodec.encodeType(event));
        }
    };
    static Context.ResultNotify RNotify = new Context.ResultNotify() {
        @Override
        public void callback(int code, Object result) {
            System.out.println("\n~~~ receive result from Redis-MQ: " + code + " ~~~");
            if ( XmlFormat )
                try { Request.printXml(result); } catch (Exception e) { e.printStackTrace(); }
            else
                Request.printJson(JsonCodec.encodeType(result));
        }
    };

    static Context context = null;

    // 获取命令行输入
    static String getInput(BufferedReader input, String tip) throws Exception {
        if ( tip == null ) {
            tip = DMode ? Host + "#" + Port : "router";
            if ( UseService != null )
                tip = tip + "~" + UseService;
            tip = "\n" + tip + "> ";
        }
        System.out.print(tip);
        String str = input.readLine();
        System.out.println();
        return str == null ? null : str.trim();
    }

    /** 主程序入口 */
    public static void main(String[] args) throws Exception {
        if ( args.length > 0 )
            Host = args[0];
        if ( args.length > 1 )
            Port = Integer.parseInt(args[1]);

        System.out.println("\nUBSI Consumer Console Utility, press ENTER for help");

        Context.setLogApp(InetAddress.getLocalHost().getHostName(), "rewin.ubsi.cli.Console");
        Context.startup(".");
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        boolean loop = true;
        while ( loop ) {
            try {
                String str = getInput(input, null);
                if ( str == null )
                    break;
                String[] cmd = split(str);
                if ( cmd.length == 0 ) {
                    help();
                    continue;
                }

                if ( UseService != null ) {
                    if ( "?".equals(cmd[0]) ) {
                        if ( cmd.length > 1 )
                            entry(new String[] { null, UseService, cmd[1] });
                        else
                            entry(new String[] { null, UseService });
                    } else if ( ".".equals(cmd[0]) ) {
                        UseService = null;
                    } else {
                        List<String> cmds = Util.toList(null, UseService, cmd[0]);
                        for ( int i = 1; i < cmd.length; i ++ )
                            cmds.add(cmd[i]);
                        request(cmds.toArray(new String[cmds.size()]));
                    }
                    continue;
                }

                switch ( cmd[0] ) {
                    case "quit":
                    case "exit":        loop = false; break;
                    case "help":        help(); break;
                    case "direct":      direct(cmd); break;
                    case "call":        call(); break;
                    case "request":     request(cmd); break;
                    case "use":         use(cmd); break;
                    case "xml":         xml(); break;
                    case "json":        json(); break;
                    case "service":     service(); break;
                    case "entry":       entry(cmd); break;
                    case "config":      config(cmd); break;
                    case "header":      header(cmd); break;
                    case "version":     version(cmd); break;
                    case "router":      router(cmd); break;
                    case "timeout":     timeout(cmd); break;
                    case "alone":       alone(cmd); break;
                    case "async":       async(cmd); break;
                    case "time":        time(); break;
                    case "register":    register(cmd); break;
                    case "tracelog":    tracelog(cmd); break;
                    case "jedis":       jedis(cmd); break;
                    case "subscribe":   subscribe(cmd); break;
                    case "unsubscribe": unsubscribe(cmd); break;
                    case "publish":     publish(cmd); break;
                    case "event":       event(cmd); break;
                    case "statistics":  statistics(); break;
                    default:            System.out.println("Unknown Command: " + cmd[0]);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        Context.shutdown();

        System.out.println("UBSI Consumer Console is shutting-down ...");
    }

    // split string
    static String[] split(String param) {
        param = param.trim();
        if ( param.isEmpty() )
            return new String[0];
        String[] ps = param.split(" ");
        List<String> res = new ArrayList<>();
        for ( String str : ps ) {
            str = str.trim();
            if ( !str.isEmpty() )
                res.add(str);
        }
        ps = res.toArray(new String[res.size()]);
        res.clear();
        // 处理'"'以及"\""
        for ( int i = 0; i < ps.length; i ++ ) {
            if ( ps[i].charAt(0) != '"' ) {
                res.add(ps[i]);
                continue;
            }
            ps[i] = ps[i].substring(1);
            String str = "";
            for ( int idx = i; i < ps.length; i ++ ) {
                if ( !ps[i].isEmpty() && ps[i].charAt(ps[i].length() - 1) == '"' ) {
                    if ( ps[i].length() == 1 || ps[i].charAt(ps[i].length() - 2) != '\\' ) {
                        str += (i == idx ? "" : " ") + ps[i].substring(0, ps[i].length() - 1);
                        break;
                    }
                }
                str += (i == idx ? "" : " ") + ps[i];
            }
            res.add(str.replaceAll("\\\\\"", "\""));
        }
        return res.toArray(new String[res.size()]);
    }

    // 获得Context请求实例
    static Context getContext(String service, Object... entryAndParams) throws Exception {
        Context ctx = Context.request(service, entryAndParams);
        ctx.setLogAccess(TraceLog);
        ctx.setTimeout(Timeout);
        if ( ConnectAlone )
            ctx.setConnectAlone(true);
        ctx.setHeader(Header);
        ctx.setVersion(VerMin, VerMax, VerRel);
        return ctx;
    }

    // request service entry ...
    static void request(String[] cmd) throws Exception {
        if ( cmd.length <= 2 )
            System.out.println("Syntax: request {serviceName} {entryName} params...]");
        else {
            try {
                Object[] params = new Object[cmd.length - 2];
                params[0] = cmd[2];
                for ( int i = 3; i < cmd.length; i ++ )
                    params[i-2] = XmlFormat ? XmlCodec.decode(cmd[i]) : JsonCodec.fromJson(cmd[i]);
                context = getContext(cmd[1], params);
                if ( AsyncMode ) {
                    if ( DMode )
                        context.directAsync(Host, Port, RNotify, true);
                    else
                        context.callAsync(RNotify, true);
                } else {
                    Object res = DMode ? context.direct(Host, Port) : context.call();
                    if (XmlFormat)
                        Request.printXml(res);
                    else
                        Request.printJson(JsonCodec.encodeType(res));
                }
            } catch (Context.ResultException e) {
                System.out.println("request error: " + e.toString());
            }
        }
    }
    // use [service]
    static void use(String[] cmd) {
        UseService = cmd.length > 1 ? cmd[1] : null;
    }
    // service
    static void service() throws Exception {
        try {
            context = Context.request("", "getRuntime", null);
            Info.Runtime res = Codec.toType(context.direct(Host, Port), Info.Runtime.class);
            for ( String sname : res.services.keySet() ) {
                Info.SRuntime srv = res.services.get(sname);
                System.out.println("[" + srv.status + "] " + (sname.isEmpty()?"\"\"":sname) + ": " + srv.version + (srv.release?"":"-SNAPSHOT") + ", " + srv.class_name + ", " + srv.tips);
            }
        } catch (Context.ResultException e) {
            System.out.println("request error: " + e.toString());
        }
    }
    // entry service [entry]
    static void entry(String[] cmd) throws Exception {
        if ( cmd.length <= 1 )
            System.out.println("Syntax: entry {serviceName} [{entryName}]");
        else {
            try {
                context = Context.request("", "getEntry", cmd[1]);
                List<Object> res = (List<Object>)context.direct(Host, Port);
                boolean newline = false;
                for ( Object o : res ) {
                    Info.Entry ent = Codec.toType(o, Info.Entry.class);
                    if ( cmd.length > 2 && !ent.name.equals(cmd[2]) )
                        continue;
                    if ( newline )
                        System.out.println();
                    System.out.println(ent.name + "(): " + ent.tips);
                    if ( ent.params != null && !ent.params.isEmpty() ) {
                        System.out.println("参数：");
                        for ( Info.Param param : ent.params )
                            System.out.println("  " + param.name + ": " + param.type + ", " + param.tips);
                    }
                    if ( ent.result != null ) {
                        System.out.println("返回：");
                        System.out.println("  " + ent.result.type + ", " + ent.result.tips);
                    }
                    newline = true;
                }
            } catch (Context.ResultException e) {
                System.out.println("request error: " + e.toString());
            }
        }
    }
    // statistics
    static void statistics() {
        Request.printJson(Context.getStatistics());
    }
    // jedis
    static void jedis(String[] cmd) throws Exception {
        if ( !JedisUtil.isInited() )
            System.out.println("Jedis isn't initialized");
        else {
            int[] pools = JedisUtil.getPools();
            System.out.println("Jedis pools - Active: " + pools[0] + ", Idle: " + pools[1]);
        }
    }
    // subscribe [channel|pattern#channel|event#channel ...]
    static void subscribe(String[] cmd) throws Exception {
        if ( !JedisUtil.isInited() ) {
            System.out.println("Jedis isn't initialized");
            return;
        }
        for ( int i = 1; i < cmd.length; i ++ ) {
            if ( cmd[i].startsWith("event#") ) {
                String channel = cmd[i].substring("event#".length());
                if (!channel.isEmpty())
                    JListener.subscribeEvent(channel);
            } else if ( cmd[i].startsWith("pattern#") ) {
                String channel = cmd[i].substring("pattern#".length());
                if (!channel.isEmpty())
                    JListener.subscribePattern(channel);
            } else
                JListener.subscribe(cmd[i]);
        }
        System.out.println("subscribe channels:");
        System.out.println("  channel: " + new Gson().toJson(JedisUtil.getSubChannels()));
        System.out.println("  pattern: " + new Gson().toJson(JedisUtil.getPSubChannels()));
        System.out.println("    event: " + new Gson().toJson(JedisUtil.getEventChannels()));
    }
    // unsubscribe [channel|pattern#channel|event#channel ...]
    static void unsubscribe(String[] cmd) {
        if ( !JedisUtil.isInited() )
            System.out.println("Jedis isn't initialized");
        else if ( cmd.length > 1 ) {
            for (int i = 1; i < cmd.length; i++) {
                if (cmd[i].startsWith("event#")) {
                    String channel = cmd[i].substring("event#".length());
                    if (!channel.isEmpty())
                        JListener.unsubscribeEvent(channel);
                } else if (cmd[i].startsWith("pattern#")) {
                    String channel = cmd[i].substring("pattern#".length());
                    if (!channel.isEmpty())
                        JListener.unsubscribePattern(channel);
                } else
                    JListener.unsubscribe(cmd[i]);
            }
        } else {
            JListener.unsubscribe();
            JListener.unsubscribePattern();
            JListener.unsubscribeEvent();
        }
    }
    // publish channel data ...
    static void publish(String[] cmd) throws Exception {
        if ( !JedisUtil.isInited() )
            System.out.println("Jedis isn't initialized");
        else if ( cmd.length <= 2 )
            System.out.println("Syntax: publish channel data ...");
        else
            for ( int i = 2; i < cmd.length; i ++ )
                JedisUtil.publish(cmd[1], XmlFormat ? XmlCodec.decode(cmd[i]) : JsonCodec.fromJson(cmd[i]));
    }
    // event channel data ...
    static void event(String[] cmd) throws Exception {
        if ( !JedisUtil.isInited() )
            System.out.println("Jedis isn't initialized");
        else if ( cmd.length <= 2 ) {
            System.out.println("Syntax: event channel data ...\n");
            System.out.print("event counts:");
            Request.printJson(JedisUtil.getEventCounts());
        } else
            for ( int i = 2; i < cmd.length; i ++ )
                JedisUtil.putEvent(cmd[1], XmlFormat ? XmlCodec.decode(cmd[i]) : JsonCodec.fromJson(cmd[i]));
    }
    // config [router|log]
    static void config(String[] cmd) throws Exception {
        if ( cmd.length <= 1 ) {
            System.out.print("consumer config: ");
            Request.printJson(Context.getConfig());
        } else if ( "router".equalsIgnoreCase(cmd[1]) ) {
            System.out.print("local router: ");
            Request.printJson(Context.getRouteTable());
        } else if ( "log".equalsIgnoreCase(cmd[1]) ) {
            System.out.print("log config: ");
            Request.printJson(Context.getLogConfig());
        } else
            System.out.println("Unknown config type: " + cmd[1]);
    }
    // header [key [value]]
    static void header(String[] cmd) throws Exception {
        if ( cmd.length > 1 ) {
            String key = cmd[1];
            if ( cmd.length > 2 ) {
                Object value = XmlFormat ? XmlCodec.decode(cmd[2]) : JsonCodec.fromJson(cmd[2]);
                Header.put(key, value);
            } else
                Header.remove(key);
        }
        System.out.print("request's header: ");
        if ( XmlFormat )
            Request.printXml(Header);
        else
            Request.printJson(JsonCodec.encodeType(Header));
    }
    // version [min max release]
    static void version(String[] cmd) {
        if ( cmd.length > 3 )
            VerRel = Integer.parseInt(cmd[3]);
        if ( cmd.length > 2 )
            VerMax = Util.getVersion(cmd[2]);
        if ( cmd.length > 1 )
            VerMin = Util.getVersion(cmd[1]);
        System.out.println("version limit when routing:");
        System.out.println("  min_ver: " + Util.getVersion(VerMin));
        System.out.println("  max_ver: " + Util.getVersion(VerMax) + (VerMax == 0 ? " (no limit)" : ""));
        System.out.println("  release: " + VerRel + (VerRel < 0 ? " (no limit)" : (VerRel == 0 ? " (not release)" : " (release)")));
    }
    // router service
    static void router(String[] cmd) throws Exception {
        if ( cmd.length <= 1 )
            System.out.println("Syntax: router {serviceName}");
        else {
            try {
                Context ctx = Context.request(cmd[1], "none");
                Object[] res = ctx.getRouter();
                System.out.println("route path: " + (res.length <= 1 ? "mocked" : res[0] + "#" + res[1]));
            } catch (Context.ResultException e) {
                System.out.println("route error: " + e.getMessage());
            }
        }
    }
    // timeout [seconds]
    static void timeout(String[] cmd) {
        if ( cmd.length > 1 )
            Timeout = Integer.parseInt(cmd[1]);
        System.out.println("request's timeout: " + Timeout + " (seconds)");
    }
    // alone [on|off]
    static void alone(String[] cmd) {
        if ( cmd.length > 1 ) {
            switch (cmd[1]) {
                case "on":  ConnectAlone = true; break;
                case "off": ConnectAlone = false; break;
            }
        }
        System.out.println("request use alone connection: " + (ConnectAlone ? "on" : "off"));
    }
    // async [on|off]
    static void async(String[] cmd) {
        if ( cmd.length > 1 ) {
            switch (cmd[1]) {
                case "on":  AsyncMode = true; break;
                case "off": AsyncMode = false; break;
            }
        }
        System.out.println("receive result through Redis-MQ: " + (AsyncMode ? "on" : "off"));
    }
    // time
    static void time() {
        long time = context != null ? context.getResultTime() : 0;
        System.out.println("lastest request takes time: " + time + " (milli-seconds)");
    }
    // register container|restful
    static void register(String[] cmd) throws Exception {
        if ( cmd.length > 1 ) {
            switch (cmd[1]) {
                case "container":
                    Request.printJson(Context.getRegister());
                    return;
                case "container-raw":
                    if ( !JedisUtil.isInited() )
                        System.out.println("Jedis isn't initialized");
                    else
                        Request.printJson(Context.getRegister(Context.REG_CONTAINER, Object.class));
                    return;
                case "restful":
                    if ( !JedisUtil.isInited() )
                        System.out.println("Jedis isn't initialized");
                    else
                        Request.printJson(Context.getRegister(Context.REG_RESTFUL, Object.class));
                    return;
                case "clear":
                    if ( !JedisUtil.isInited() )
                        System.out.println("Jedis isn't initialized");
                    else {
                        try (Jedis jedis = JedisUtil.getJedis()) {
                            jedis.del(Context.REG_CONTAINER);
                            jedis.del(Context.REG_RESTFUL);
                        }
                    }
                    return;
            }
        }
        System.out.println("Syntax: register container|container-raw|restful|clear");
    }
    // tracelog [on|off]
    static void tracelog(String[] cmd) {
        if ( cmd.length > 1 ) {
            switch (cmd[1]) {
                case "on":  TraceLog = true; break;
                case "off": TraceLog = false; break;
            }
        }
        System.out.println("request's trace log: " + (TraceLog ? "on" : "off"));
    }
    // xml
    static void xml() {
        System.out.println("data format: XML");
        XmlFormat = true;
        System.out.println(Request.XML_FORMAT);
    }
    // json
    static void json() {
        System.out.println("data format: JSON");
        XmlFormat = false;
        System.out.println(Request.JSON_FORMAT);
    }
    // direct [host [port]]
    static void direct(String[] cmd) {
        if (cmd.length > 2)
            Port = Integer.parseInt(cmd[2]);
        if ( cmd.length > 1 )
            Host = cmd[1];
        DMode = true;
        System.out.println("switch to direct mode");
    }
    // call
    static void call() {
        DMode = false;
        System.out.println("switch to router mode");
    }
    // help
    static void help() {
        String[][] cmd = {
                new String[] { "async [on|off]", "show or set receive result through Redis-MQ" },
                new String[] { "direct [host [port]]", "request direct to host#port (switch to direct mode)" },
                new String[] { "call", "request call to routed Container (switch to router mode)" },
                new String[] { "request service entry ...", "send request synchronized (use ' instead of \" in json mode parameters)" },
                new String[] { "use service", "use spec service" },
                new String[] { "xml", "set XML data format" },
                new String[] { "json", "set JSON data format" },
                new String[] { "service", "show services in Container (direct mode)" },
                new String[] { "entry service [entry]", "show service's entry in Container (direct mode)" },
                new String[] { "config [router|log]", "show local config for Consumer [route|log]" },
                new String[] { "header [key [value]]", "show or set|clear request header's key-value" },
                new String[] { "version [min max release]", "show or set request service's version" },
                new String[] { "router service", "get service's routed path" },
                new String[] { "timeout [seconds]", "show or set request's timeout" },
                new String[] { "alone [on|off]", "show or set connect alone (use for router mode)" },
                new String[] { "time", "show request's result time (milli-seconds)" },
                new String[] { "register container|container-raw|restful|clear", "show register data of Containers or Restful-Consumer, or clear all register" },
                new String[] { "tracelog [on|off]", "show or set access-log's setting" },
                new String[] { "jedis", "show Jedis pools" },
                new String[] { "subscribe [channel|pattern#channel|event#channel ...]", "subscribe message or event channel" },
                new String[] { "unsubscribe [channel|pattern#channel|event#channel ...]", "unsubscribe message or event channel" },
                new String[] { "publish channel data ...", "publish message to channel" },
                new String[] { "event channel data ...", "put event to channel" },
                new String[] { "statistics", "show request's statistics report" },
        };
        String[][] cmd_use = {
                new String[] { "? [entry]", "show service's entry in Container (direct mode)" },
                new String[] { "{entry} ...", "send request" },
                new String[] { ".", "back to main command" },
        };
        if ( UseService != null )
            cmd = cmd_use;
        // 命令排序
        Arrays.sort(cmd, new Comparator<String[]>() {
            @Override
            public int compare(String[] o1, String[] o2) {
                return o1[0].compareToIgnoreCase(o2[0]);
            }
        });
        // 计算最大长度
        int max = 0;
        for ( String[] c : cmd )
            max = Math.max(max, c[0].length());

        for ( String[] c : cmd ) {
            System.out.print(c[0]);
            for ( int i = 0; i < max + 2 - c[0].length(); i ++ )
                System.out.print(" ");
            System.out.println("- " + c[1]);
        }
    }
}
