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

package rewin.ubsi.consumer;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * UBSI Consumer本地配置数据对象
 */
public class Config {

    /** Consumer当前的运行参数 */
    public static class Consumer {
        public int          io_threads = Context.IOThreads;             // I/O线程的数量
        public String       redis_host;                                 // Redis服务的主机名
        public int          redis_port = Context.RedisPort;             // Redis服务的端口号
        public String       redis_master_name;                          // Redis哨兵模式的master名字
        public Set<String>  redis_sentinel_addr;                        // Redis哨兵模式的节点地址
        public String       redis_password;                             // Redis服务的访问密码
        public int          redis_conn_idle = Context.RedisMaxIdle;     // Redis连接池的最大空闲数量
        public int          redis_conn_max = Context.RedisMaxConn;      // Redis连接池的最大数量

        public int          timeout_connect = Context.TimeoutConnection;    // 缺省的连接超时时间
        public int          timeout_request = Context.TimeoutRequest;       // 缺省的请求超时时间
        public int          timeout_reconnect = Context.TimeoutReconnect;   // 连接失败后重新尝试的间隔时间

        public List<String> filters;        // 请求过滤器的类名字
    }

    /** Consumer运行参数的说明 */
    public static class ConsumerComment {
        public String   io_threads = "I/O线程的数量，0表示\"CPU内核数 * 2\"（重启生效）";
        public String   redis_host = "Redis服务的主机名（单机模式）（重启生效）";
        public String   redis_port = "Redis服务的端口号（单机模式）（重启生效）";
        public String   redis_master_name = "Redis哨兵模式的master名字（重启生效）";
        public String   redis_sentinel_addr = "Redis哨兵模式的节点地址（多值），格式: [\"host:port\", ...]（重启生效）";
        public String   redis_password = "Redis服务的访问密码（重启生效）";
        public String   redis_conn_idle = "Redis连接池的最大空闲数量（重启生效）";
        public String   redis_conn_max = "Redis连接池的最大数量（重启生效）";

        public String   timeout_connect = "缺省的连接超时时间，秒";
        public String   timeout_request = "缺省的请求超时时间，秒";
        public String   timeout_reconnect = "连接失败后重新尝试的间隔时间，秒";

        public String   filters = "请求过滤器的类名字（多值），格式: [\"{filterClass}\", ...]";
    }

    /** consumer配置参数 */
    public Consumer         consumer;           // 当前的Consumer参数
    public Consumer         consumer_restart;   // 重启后生效的Consumer参数
    public ConsumerComment  consumer_comment;   // 配置项说明

    /** LOG配置项 */
    public static class LogItem {
        public int      output = -1;        // 输出位置
        public String   filename = null;    // 日志文件名前缀
    }
    /** LOG分类配置 */
    public static class LogOpt {
        public LogItem  all;                // 默认设置
        public LogItem  debug;
        public LogItem  info;
        public LogItem  warn;
        public LogItem  error;
        public LogItem  action;
        public LogItem  access;
        public LogItem  app;                // 应用自定义分类的设置
    }
    /** LOG强制记录Access的设置 */
    public static class LogAccess {
        public String service;              // 服务名字
        public String entry;                // 方法名字
    }
    /** LOG配置参数 */
    public static class Log {
        public LogOpt       options;        // 分类配置
        public LogAccess[]  consumer;       // 强制记录consumer请求日志的服务列表
        public LogAccess[]  container;      // 强制记录container请求日志的服务列表
        public String       slf4j_level;    // slf4j日志输出级别
        public String       js_level;       // JavaScript日志输出级别
    }
    /** LOG文件 */
    public static class LogFile {
        public String   name;               // 文件名
        public boolean  dir;                // 是否目录
        public long     size;               // 长度
        public long     time;               // 时间
    }

    /** 转换目录中的分隔符 */
    public static String repaireDir(String dir) {
        String sep = File.separator.replaceAll("\\\\", "\\\\\\\\");
        String res = dir.replaceAll("\\/", sep);
        res = res.replaceAll("\\\\", sep);
        return res;
    }
    /** 获得指定目录下的文件列表 */
    public static List<LogFile> getFileList(File dir) {
        List<Config.LogFile> res = new ArrayList<>();
        if ( !dir.exists() || !dir.isDirectory() )
            return res;
        for ( File file : dir.listFiles() ) {
            String fname = file.getName();
            LogFile flog = new LogFile();
            flog.name = fname;
            flog.dir = file.isDirectory();
            flog.size = flog.dir ? 0 : file.length();
            flog.time = file.lastModified();
            res.add(flog);
        }
        return res;
    }
    /** 读取文件内容 */
    public static Object[] readFile(File file, long offset, int length) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long size = raf.length();
            if ( offset < 0 )
                offset = 0;
            if ( offset >= size || length <= 0 )
                return new Object[] { size, new byte[0] };
            if ( length > size - offset )
                length = (int)(size - offset);
            byte[] buf = new byte[length];
            raf.seek(offset);
            raf.readFully(buf);
            return new Object[] { size, buf };
        }
    }
}
