package rewin.ubsi.cli;

import rewin.ubsi.common.LogUtil;
import rewin.ubsi.common.ScriptUtil;
import rewin.ubsi.consumer.Context;
import rewin.ubsi.container.Bootstrap;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.text.DecimalFormat;
import java.util.TreeMap;

/**
 * 命令行工具：执行JavaScript脚本
 */
public class Script {

    /** 主程序入口 */
    public static void main(String[] args) throws Exception {
        String host = null;
        int port = Bootstrap.DEFAULT_PORT;
        String file = "script.js";
        for (int i = 0; i < args.length; i++) {
            if ("-h".equals(args[i])) {
                host = args[i + 1];
                i++;
            } else if ("-p".equals(args[i])) {
                port = Integer.parseInt(args[i + 1]);
                i++;
            } else
                file = args[i];
        }

        if (!new File(file).exists()) {
            System.out.println("\nError: JavaScript file \"" + file + "\" not found!\n");
            System.out.println("Usage: Script [script.js] [-h host] [-p port]\n");
            System.out.println("Api of '$' in JavaScript:");
            for (String key : new TreeMap<String, String>(ScriptUtil.Api).keySet())
                System.out.println("  " + key + "; " + ScriptUtil.Api.get(key));
            return;
        }

        String js = null;
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[in.available()];
            in.read(buf);
            js = new String(buf, "utf-8");
        }

        Context.setLogApp(InetAddress.getLocalHost().getHostName(), "rewin.ubsi.cli.Script");
        Context.startup(".");

        ScriptUtil scriptUtil = new ScriptUtil("rewin.ubsi.cli", "rewin.ubsi.cli.Script", "script");
        if ( host != null )
            scriptUtil.host(host, port);

        long time = System.currentTimeMillis();
        Exception e = null;
        try {
            ScriptUtil.runJs(js, scriptUtil, null);
        } catch (Exception ee) {
            e = ee;
        }

        Context.shutdown();

        System.out.println("\n执行过程：");
        DecimalFormat df = new DecimalFormat("000.000");
        for ( ScriptUtil.Message msg : scriptUtil.Messages ) {
            String out = df.format((double)(msg.time - time) / 1000) + " ";
            switch (msg.type) {
                case LogUtil.DEBUG:
                    out += "[DEBUG]";
                    break;
                case LogUtil.INFO:
                    out += "[INFO]";
                    break;
                case LogUtil.ERROR:
                    out += "[ERROR]";
                    break;
                default:
                    out += "[???]";
                    break;
            }
            System.out.println(out + " " + msg.text);
        }

        System.out.println("\n执行结果：");
        if ( scriptUtil.Result != null && (scriptUtil.Result instanceof Throwable) )
            System.out.println(scriptUtil.Result.toString());
        else
            Request.printJson(scriptUtil.Result);

        if ( e != null ) {
            System.out.println();
            try { Thread.sleep(500); } catch (Exception ee) {}
            e.printStackTrace();
        }
    }
}
