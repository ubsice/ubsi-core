package rewin.ubsi.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import rewin.ubsi.consumer.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * UBSI日志工具
 */
public class LogUtil {

    /** 定义日志分类 */
    public final static int DEBUG = -1;         // 调式
    public final static int INFO = -2;          // 信息
    public final static int WARN = -3;          // 警告
    public final static int ERROR = -4;         // 错误
    public final static int ACTION = -5;        // 用户操作
    public final static int ACCESS = -6;        // 访问请求

    /** 定义日志的输出 */
    public final static int OUT_CONSOLE = 0x01; // 控制台输出
    public final static int OUT_FILE = 0x02;    // 本地文件
    public final static int OUT_REMOTE = 0x04;  // 远程微服务

    public final static String LOG_SERVICE = "rewin.ubsi.logger";   // 远程微服务的名字
    public final static String LOG_ENTRY = "log";                   // 远程微服务的方法

    public static String    App_Addr;        // 应用所在的位置

    public static String    File_Path = "logs";     // 日志文件的路径
    public static String    Field_Separator = "\t"; // 缺省的字段分隔符
    public static int       File_Separator = 1;     // 文件分割方式

    public static String    Default_File = "run";           // 缺省的文件名前缀
    public static String    Debug_File;                     // 分类日志的文件名前缀
    public static String    Info_File;
    public static String    Warn_File;
    public static String    Error_File;
    public static String    Action_File = "opr";
    public static String    Access_File = "req";
    public static String    App_File = "app";               // 应用自定义分类日志的文件名前缀

    public static int       Default_Output = OUT_CONSOLE;   // 缺省的输出方式
    public static int       Debug_Output = -1;              // 分类日志的输出方式
    public static int       Info_Output = -1;
    public static int       Warn_Output = -1;
    public static int       Error_Output = -1;
    public static int       Action_Output  = -1;
    public static int       Access_Output = -1;
    public static int       App_Output = -1;                // 应用自定义分类日志的输出方式

    /* 获得日志类型的显示字符串 */
    static String getType(int type) {
        switch (type) {
            case DEBUG:     return "[DEBUG]";
            case INFO:      return "[INFO]";
            case WARN:      return "[WARN]";
            case ERROR:     return "[ERROR]";
            case ACTION:    return "[ACTION]";
            case ACCESS:    return "[ACCESS]";
        }
        return "[APP#" + type + "]";
    }

    /* 获得日志类型的输出方式 */
    static int getOutput(int type) {
        int output = -1;
        switch (type) {
            case DEBUG:     output = Debug_Output; break;
            case INFO:      output = Info_Output; break;
            case WARN:      output = Warn_Output; break;
            case ERROR:     output = Error_Output; break;
            case ACTION:    output = Action_Output; break;
            case ACCESS:    output = Access_Output; break;
            default:        output = App_Output; break;
        }
        return output < 0 ? Default_Output : output;
    }

    /* 后台任务线程 */
    static WorkThread Worker = null;

    static class WorkThread extends Thread {
        boolean Running = true;     // 正在运行
        boolean Shutdown = false;   // 是否退出
        ConcurrentLinkedQueue<Object[]> Buffer = new ConcurrentLinkedQueue<>();
        SimpleDateFormat Format1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        SimpleDateFormat Format2 = new SimpleDateFormat("yyyyMM");
        SimpleDateFormat Format3 = new SimpleDateFormat("yyyyMMdd");
        Gson Json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();     //new Gson();

        /* 添加1条待处理日志 */
        void add(Object[] log) {
            Buffer.offer(log);
            if ( Buffer.size() >= 100 )
                synchronized(this) { this.notifyAll(); }
        }

        /* 添加1条日志到日志文件 */
        void write(int type, String log, Date time, Object data) throws Exception {
            String fname = null;
            switch (type) {
                case DEBUG:     fname = Debug_File; break;
                case INFO:      fname = Info_File; break;
                case WARN:      fname = Warn_File; break;
                case ERROR:     fname = Error_File; break;
                case ACTION:    fname = Action_File; break;
                case ACCESS:    fname = Access_File; break;
                default:        fname = App_File; break;
            }
            if ( fname == null )
                fname = Default_File;
            if ( File_Separator > 0 ) {
                String month = Format2.format(time);
                if ( File_Separator == 1 )
                    fname += "_" + month;
                else
                    fname = month + File.separator + fname + "_" + Format3.format(time);
            }
            File file = new File(File_Path + File.separator + fname + ".log");
            Util.checkFilePath(file);
            try ( PrintStream out = new PrintStream(new FileOutputStream(file, true), false, "utf-8") ) {
                out.println(log);
                if ( data instanceof Throwable )
                    ((Throwable)data).printStackTrace(out);
            }
        }

        public void run() {
            while ( !Shutdown || !Buffer.isEmpty() ) {
                while ( true ) {
                    Object[] rec = Buffer.poll();
                    if ( rec == null )
                        break;
                    try {
                        Date time = new Date((Long)rec[0]);
                        int type = (Integer)rec[1];
                        int output = getOutput((Integer)rec[1]);
                        Object data = rec[rec.length - 1];
                        if ( data instanceof Throwable ) {
                            data = Util.getTargetThrowable((Throwable) data);
                            rec[rec.length - 1] = ((Throwable) data).toString();
                        }
                        if ( (output & OUT_REMOTE) != 0 ) {
                            Context context = Context.request(LOG_SERVICE, LOG_ENTRY, rec);
                            try { context.callAsync(null, false); } catch (Exception e) {}
                        }
                        if ( (output & OUT_CONSOLE) != 0 || (output & OUT_FILE) != 0 ) {
                            StringBuffer sb = new StringBuffer();
                            sb.append(getType(type));
                            sb.append(Field_Separator);
                            sb.append(Format1.format(time));
                            for ( int i = 2; i < rec.length - 1; i ++ ) {
                                sb.append(Field_Separator);
                                sb.append(rec[i]);
                            }
                            sb.append(Field_Separator);
                            Object body = rec[rec.length - 1];
                            if ( body instanceof CharSequence ) {
                                String str = ((CharSequence)body).toString();
                                if ( str.indexOf('\n') >= 0 ) {
                                    sb.append("\"...\"\n");
                                    sb.append(str);
                                } else
                                    sb.append(Json.toJson(body));
                            } else {
                                String str = Json.toJson(body);
                                if ( str.indexOf('\n') >= 0 )
                                    sb.append("...\n");
                                sb.append(str);
                            }

                            String str = sb.toString();
                            if ( (output & OUT_CONSOLE) != 0 ) {
                                System.out.println(str);
                                if ( data instanceof Throwable )
                                    ((Throwable)data).printStackTrace(System.out);
                            }
                            if ( (output & OUT_FILE) != 0 )
                                try { write(type, str, time, data); } catch (Exception e) {}
                        }
                    } catch (Exception e) {}
                }
                if ( !Shutdown ) {
                    synchronized (this) {
                        try { this.wait(1000); } catch (Exception e) { }
                    }
                }
            }
            Running = false;
        }
    }

    /** 初始化 */
    public static void start() {
        if ( Worker == null ) {
            Worker = new WorkThread();
            Worker.start();
        }
    }

    /** 结束 */
    public static void stop() {
        if ( Worker != null ) {
            Worker.Shutdown = true;
            synchronized (Worker) {
                Worker.notifyAll();
            }
            long t = System.currentTimeMillis();
            while ( Worker.Running ) {
                try { Thread.sleep(100); } catch (Exception e) {}
                if ( System.currentTimeMillis() - t > 3000 )
                    break;
            }
            Worker = null;
        }
    }

    /** 输出一条日志 */
    public static void log(int type, String appTag, String appID, Throwable callStack, int stackIndex, String tips, Object body) {
        try {
            String code = "[" + Thread.currentThread().getId() + "]";
            if (callStack != null) {
                StackTraceElement[] stack = callStack.getStackTrace();
                if (stackIndex >= 0 && stackIndex < stack.length)
                    code += stack[stackIndex].getClassName() + "#" + stack[stackIndex].getMethodName() + "()#" + stack[stackIndex].getLineNumber();
            }
            Object[] rec = new Object[]{   // 每条日志的结构
                    System.currentTimeMillis(), // long，日志产生的时间戳
                    type,                       // int，日志分类
                    App_Addr,                   // String，应用所在的位置
                    appTag,                     // String，应用分类
                    appID,                      // String，应用ID
                    code,                       // String，[线程ID]调用类#方法#行号
                    tips,                       // String，日志内容
                    body                        // Object，附加内容
            };
            Worker.add(rec);
        } catch (Exception e) {}
    }
}
