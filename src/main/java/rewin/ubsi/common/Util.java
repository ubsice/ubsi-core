package rewin.ubsi.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import rewin.ubsi.annotation.USNote;
import rewin.ubsi.annotation.USNotes;

import java.io.*;
import java.lang.reflect.*;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * 常用工具
 */
public class Util {

    static SimpleDateFormat TimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    /** 返回时间字符串 yyyy-MM-dd HH:mm:ss.SSS */
    public static String strTime(long t) {
        return TimeFormat.format(new Date(t));
    }

    /** 将毫秒数转换为 hh:mm:ss.xxx */
    public static String toHMS(long n) {
        long ms = n % 1000; // 毫秒
        n = n / 1000;
        long s = n % 60;    // 秒
        n = n / 60;
        long m = n % 60;    // 分钟
        n = n / 60;         // 小时
        DecimalFormat xx = new DecimalFormat("00");
        DecimalFormat xxx = new DecimalFormat("000");
        return xx.format(n) + ":" + xx.format(m) + ":" + xx.format(s) + "." + xxx.format(ms);
    }

    /** 检查最大最小值 */
    public static int checkMinMax(int i, int min, int max) {
        return i < min ? min : (i > max ? max : i);
    }

    /** 检查字符串是否为null或""，返回null表示"空"字符串，否则返回trim() */
    public static String checkEmpty(String str) {
        if ( str == null )
            return null;
        str = str.trim();
        return str.isEmpty() ? null : str;
    }

    /** 检查字符串中是否包含指定字符 */
    public static boolean checkString(String str, String chars) throws Exception {
        for ( int c : chars.chars().toArray() )
            if ( str.indexOf(c) >= 0 )
                return true;
        return false;
    }

    /** 用原子变量记录更大的值 */
    public static boolean setLarger(AtomicLong atomic, long value) {
        while ( true ) {
            long last = atomic.get();
            if ( value <= last )
                return false;
            if ( atomic.compareAndSet(last, value) )
                return true;
        }
    }

    /** 获得UUID */
    public static String getUUID() {
        return new ObjectId().toHexString();
    }

    /** 转换版本号 */
    public static int getVersion(String ver) {
        if ( ver == null )
            return 0;
        String[] ss = ver.split("\\.");
        int[] mul = { 1000 * 1000, 1000, 1 };
        int res = 0;
        for ( int i = 0; i < 3 && i < ss.length; i ++ ) {
            int n = 0;
            try { n = Integer.parseInt(ss[i]); } catch (Exception e) {}
            res += n * mul[i];
        }
        return res;
    }
    public static String getVersion(int ver) {
        int v1 = ver / (1000 * 1000);
        int v2 = (ver % (1000 * 1000)) / 1000;
        int v3 = ver % 1000;
        return v1 + "." + v2 + "." + v3;
    }

    /** 匹配字符串，pattern中的"*"表示通配符 */
    public static boolean matchString(String str, String pattern) {
        if ( pattern == null )
            return true;
        if ( str == null )
            return false;
        int at = pattern.indexOf('*');
        if ( at < 0 )
            return pattern.equals(str);
        if ( at > 0 )
            return str.startsWith(pattern.substring(0, at));
        return true;
    }

    /** 将Object...转换为Map */
    public static Map toMap(Object... list) {
        if ( list == null )
            return null;
        Map map = new HashMap();
        for ( int i = 0; i < list.length - 1; i += 2 )
            map.put(list[i], list[i+1]);
        return map;
    }
    /** 将Object...转换为Bson */
    public static Bson toBson(Object... list) {
        if ( list == null )
            return null;
        Document doc = new Document();
        for ( int i = 0; i < list.length - 1; i += 2 )
            doc.append((String)list[i], list[i+1]);
        return doc;
    }
    /** 将Object...转换为Set */
    public static Set toSet(Object... list) {
        if ( list == null )
            return null;
        Set set = new HashSet();
        for ( int i = 0; i < list.length; i ++ )
            set.add(list[i]);
        return set;
    }
    /** 将Object...转换为List */
    public static List toList(Object... list) {
        if ( list == null )
            return null;
        List res = new ArrayList();
        for ( int i = 0; i < list.length; i ++ )
            res.add(list[i]);
        return res;
    }
    /** 遍历Object，将所有的T[]转换为List（修改原值） */
    public static Object array2List(Object o) {
        if ( o == null )
            return null;
        if ( o instanceof List ) {
            List res = (List)o;
            for ( int i = 0; i < res.size(); i ++ )
                res.set(i, array2List(res.get(i)));
            return res;
        }
        if ( o instanceof Set ) {
            Set res = (Set)o;
            for ( Object x : res.toArray() ) {
                res.remove(x);
                res.add(array2List(x));
            }
            return res;
        }
        if ( o instanceof Map ) {
            Map res = (Map)o;
            for ( Object k : res.keySet().toArray() ) {
                Object x = res.remove(k);
                res.put(k, array2List(x));
            }
            return res;
        }
        Class cls = o.getClass();
        if ( cls.isArray() ) {
            List res = new ArrayList();
            int length = Array.getLength(o);
            for ( int x = 0; x < length; x ++ )
                res.add(array2List(Array.get(o, x)));
            return res;
        }
        return o;
    }

    /** 将JSON转换为Type */
    public static <T> T json2Type(String json, Type type, Type... typeArguments) {
        Gson gson = new Gson();
        if ( typeArguments == null || typeArguments.length == 0 )
            return gson.fromJson(json, type);
        return gson.fromJson(json, TypeToken.getParameterized(type, typeArguments).getType());
    }

    /** 检查文件路径 */
    public static void checkFilePath(File file) throws Exception {
        File path = file.getParentFile();
        if ( path == null )
            return;
        if ( !path.exists() )
            try { path.mkdirs(); } catch (Exception e) {}
        if ( !path.exists() )
            throw new Exception("path \"" + path.getCanonicalPath() + "\" not exists");
    }
    /** 删除文件或目录 */
    public static void rmdir(File file) {
        if ( file == null || !file.exists() )
            return;
        if ( file.isDirectory() )
            for ( File f : file.listFiles() )
                rmdir(f);
        try { file.delete(); } catch (Exception e) {}
    }

    /** 保存xml数据文件 */
    public static void saveXmlFile(File file, Object data) throws Exception {
        checkFilePath(file);
        try (RWLock locker = RWLock.lockWrite(file.getCanonicalPath())) {
            XmlCodec.encode(data, true, file);
        }
    }
    /** 读取xml数据文件 */
    public static Object readXmlFile(File file) throws Exception {
        try (RWLock locker = RWLock.lockRead(file.getCanonicalPath())) {
            return XmlCodec.decode(file);
        }
    }

    /** 保存json数据文件 */
    public static void saveJsonFile(File file, Object data) throws Exception {
        checkFilePath(file);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(data);
        try ( RWLock locker = RWLock.lockWrite(file.getCanonicalPath());
              OutputStream out = new FileOutputStream(file) ) {
            out.write(json.getBytes("utf-8"));
        }
    }
    /** 读取json数据文件，文件不存在返回null */
    public static <T> T readJsonFile(File file, Type type, Type... typeArguments) throws Exception {
        if ( !file.exists() )
            return null;
        try ( RWLock locker = RWLock.lockRead(file.getCanonicalPath());
              InputStream in = new FileInputStream(file) ) {
            byte[] buf = new byte[in.available()];
            in.read(buf);
            String json = new String(buf, "utf-8");
            return json2Type(json, type, typeArguments);
        }
    }

    /** 获取实际的异常实例 */
    public static Throwable getTargetThrowable(Throwable cause) {
        while ( cause instanceof InvocationTargetException )
            cause = ((InvocationTargetException)cause).getTargetException();
        return cause;
    }

    /** 计算Hash */
    public static int hash(String... str) {
        StringBuffer sb = new StringBuffer();
        for ( String s : str )
            sb.append(sb.length() == 0 ? s : "#" + s);
        return sb.toString().hashCode();
    }

    /** 提取Class的@USNotes注解 */
    public static Map<String,Map<String,String>> getUSNotes(Class... classes) {
        if ( classes == null )
            return null;
        Map<String,Map<String,String>> res = new HashMap<>();
        for ( Class cls : classes ) {
            if ( !cls.isAnnotationPresent(USNotes.class) )
                continue;
            Map<String,String> struct = new HashMap<>();
            for ( Field fd : cls.getFields() ) {
                int mod = fd.getModifiers();
                if ( Modifier.isStatic(mod) || Modifier.isFinal(mod) )
                    continue;
                if ( !fd.isAnnotationPresent(USNote.class) )
                    continue;
                USNote note = (USNote)fd.getAnnotation(USNote.class);
                struct.put(fd.getName(), fd.getGenericType().getTypeName() + ", " + note.value());
            }
            USNotes notes = (USNotes)cls.getAnnotation(USNotes.class);
            res.put(cls.getTypeName() + ": " + notes.value(), struct);
        }
        return res;
    }

    /** 分解"."分隔的路径 */
    public static Set tracePath(Set<String> paths, boolean parent, boolean child) {
        Set res = new HashSet();
        for ( String path : paths ) {
            res.add(path);
            if (child)
                res.add(childPath(path));
            if (parent) {
                int index = path.lastIndexOf('.');
                while (index > 0) {
                    path = path.substring(0, index);
                    res.add(path);
                    index = path.lastIndexOf('.');
                }
            }
        }
        return res;
    }

    /** 获得指定路径的子路径的正则表达式 */
    public static Pattern childPath(String path) {
        String str = (path + ".").replaceAll("\\.", "\\\\.");
        return Pattern.compile("^" + str + ".*$");
    }

    /** 检查非法的路径表达式，path必须非null或"" */
    public static boolean checkPath(String path) {
        return path.indexOf('.') != 0 && path.lastIndexOf('.') != (path.length() - 1);
    }

    /** 获得模糊匹配的正则表达式 */
    public static Pattern fuzzyMatch(String str, boolean head, boolean tail, boolean case_insensitive) {
        str = str.replaceAll("\\.", "\\\\.");
        str = (head ? "^" : "^.*") + str + (tail ? "$" : ".*$");
        return case_insensitive ? Pattern.compile(str, Pattern.CASE_INSENSITIVE) : Pattern.compile(str);
    }

    /** 合并多个byte[] */
    public static byte[] mergeBytes(byte[]... bl) {
        int length = 0;
        for ( byte[] b : bl )
            length += b.length;
        byte[] res = new byte[length];
        length = 0;
        for ( int i = 0; i < bl.length; i ++ ) {
            System.arraycopy(bl[i], 0, res, length, bl[i].length);
            length += bl[i].length;
        }
        return res;
    }

    /** 比较两个byte[] */
    public static boolean compareBytes(byte[] b1, byte[] b2) {
        if ( b1 == b2 )
            return true;
        if ( b1.length != b2.length )
            return false;
        for ( int i = b1.length - 1; i >= 0; i -- )
            if ( b1[i] != b2[i] )
                return false;
        return true;
    }

    /**
     * 碰到无法进行DNS解析的IPv6地址会导致JVM进程崩溃，错误信息：
         # A fatal error has been detected by the Java Runtime Environment:
         #
         #  SIGSEGV (0xb) at pc=0x00007fccf37c8a91, pid=4380, tid=0x00007fcd03fff700
         #
         # JRE version: Java(TM) SE Runtime Environment (8.0_333-b02) (build 1.8.0_333-b02)
         # Java VM: Java HotSpot(TM) 64-Bit Server VM (25.333-b02 mixed mode linux-amd64 compressed oops)
         # Problematic frame:
         # C  [libresolv.so.2+0x7a91]  __libc_res_nquery+0x1c1
         ......
         Stack: [0x00007fcd03eff000,0x00007fcd04000000],  sp=0x00007fcd03ffb4a0,  free space=1009k
         Native frames: (J=compiled Java code, j=interpreted, Vv=VM code, C=native code)
         C  [libresolv.so.2+0x7a91]  __libc_res_nquery+0x1c1
         C  [libresolv.so.2+0x7fd1]

         Java frames: (J=compiled Java code, j=interpreted, Vv=VM code)
         j  java.net.Inet6AddressImpl.lookupAllHostAddr(Ljava/lang/String;)[Ljava/net/InetAddress;+0
         ......
     * 解决办法：
     *  禁用IPv6，启动参数 -Djava.net.preferIPv4Stack=true，或者是使用下面的方法
     * 参考资料：
     *  https://blog.csdn.net/nasen512/article/details/115554252
     */
    public static void disbaleIpv6ForJava8() {
        System.setProperty("java.net.preferIPv4Stack", "true");
    }
}
