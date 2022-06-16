package rewin.ubsi.container;

import rewin.ubsi.common.LogUtil;
import rewin.ubsi.common.Util;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * UBSI热部署的JAR包管理器
 */
class LibManager {

    final static String LIB_FILE = "rewin.ubsi.lib.json";
    final static String LIB_PATH = "rewin.ubsi.libs";

    static Set<Info.GAV> SysLib = Util.toSet(            // 系统缺省的JAR包
        new Info.GAV("com.google.code.gson", "gson", ""),//""2.8.9"),
        new Info.GAV("io.netty", "netty-all", ""),//"4.1.70.Final"),
        new Info.GAV("io.netty", "netty-buffer", ""),//"4.1.70.Final"),
        new Info.GAV("io.netty", "netty-codec", ""),//"4.1.70.Final"),
        new Info.GAV("io.netty", "netty-codec-dns", ""),//"4.1.70.Final"),
        new Info.GAV("io.netty", "netty-codec-haproxy", ""),//"4.1.70.Final"),
        new Info.GAV("io.netty", "netty-codec-http2", ""),//"4.1.70.Final"),
        new Info.GAV("io.netty", "netty-codec-http", ""),//"4.1.70.Final"),
        new Info.GAV("io.netty", "netty-codec-memcache", ""),//"4.1.70.Final"),
        new Info.GAV("io.netty", "netty-codec-mqtt", ""),//"4.1.70.Final"),
        new Info.GAV("io.netty", "netty-codec-redis", ""),//"4.1.70.Final"),
        new Info.GAV("io.netty", "netty-codec-smtp", ""),//"4.1.70.Final"),
        new Info.GAV("io.netty", "netty-codec-socks", ""),//"4.1.70.Final"),
        new Info.GAV("io.netty", "netty-codec-stomp", ""),//"4.1.70.Final"),
        new Info.GAV("io.netty", "netty-codec-xml", ""),//"4.1.70.Final"),
        new Info.GAV("io.netty", "netty-common", ""),//"4.1.70.Final"),
        new Info.GAV("io.netty", "netty-handler", ""),//"4.1.70.Final"),
        new Info.GAV("io.netty", "netty-handler-proxy", ""),//"4.1.70.Final"),
        new Info.GAV("io.netty", "netty-resolver", ""),//"4.1.70.Final"),
        new Info.GAV("io.netty", "netty-resolver-dns", ""),//"4.1.70.Final"),
        new Info.GAV("io.netty", "netty-resolver-dns-classes-macos", ""),//"4.1.70.Final"),
        //new Info.GAV("io.netty", "netty-resolver-dns-native-macos:osx-aarch_64", ""),//"4.1.70.Final"),
        new Info.GAV("io.netty", "netty-resolver-dns-native-macos", ""),//"4.1.70.Final"), 取代上下两个native包
        //new Info.GAV("io.netty", "netty-resolver-dns-native-macos:osx-x86_64", ""),//"4.1.70.Final"),
        new Info.GAV("io.netty", "netty-tcnative-classes", ""),//"4.1.70.Final"),
        new Info.GAV("io.netty", "netty-transport", ""),//"4.1.70.Final"),
        new Info.GAV("io.netty", "netty-transport-classes-epoll", ""),//"4.1.70.Final"),
        new Info.GAV("io.netty", "netty-transport-classes-kqueue", ""),//"4.1.70.Final"),
        //new Info.GAV("io.netty", "netty-transport-native-epoll:linux-aarch_64", ""),//"4.1.70.Final"),
        new Info.GAV("io.netty", "netty-transport-native-epoll", ""),//"4.1.70.Final"), 取代上下两个native包
        //new Info.GAV("io.netty", "netty-transport-native-epoll:linux-x86_64", ""),//"4.1.70.Final"),
        //new Info.GAV("io.netty", "netty-transport-native-kqueue:osx-aarch_64", ""),//"4.1.70.Final"),
        new Info.GAV("io.netty", "netty-transport-native-kqueue", ""),//"4.1.70.Final"), 取代上下两个native包
        //new Info.GAV("io.netty", "netty-transport-native-kqueue:osx-x86_64", ""),//"4.1.70.Final"),
        new Info.GAV("io.netty", "netty-transport-native-unix-common", ""),//"4.1.70.Final"),
        new Info.GAV("io.netty", "netty-transport-rxtx", ""),//"4.1.70.Final"),
        new Info.GAV("io.netty", "netty-transport-sctp", ""),//"4.1.70.Final"),
        new Info.GAV("io.netty", "netty-transport-udt", ""),//"4.1.70.Final"),
        /* Test Scope
        new Info.GAV("junit", "junit", ""),//"4.13.2"),
        new Info.GAV("org.hamcrest", "hamcrest-core", ""),//"1.3"),
        */
        new Info.GAV("org.apache.commons", "commons-pool2", ""),//"2.10.0"),
        new Info.GAV("org.bouncycastle", "bcprov-jdk15on", ""),//"1.69"),
        new Info.GAV("org.dom4j", "dom4j", ""),//"2.1.3"),
        //new Info.GAV("org.json", "json", ""),//"20211205"), with jedis 4.0.1
        new Info.GAV("org.mongodb", "mongo-java-driver", ""),//"3.12.10"),
        new Info.GAV("org.slf4j", "slf4j-api", ""),//"1.7.32"),
        //new Info.GAV("org.slf4j", "slf4j-nop", ""),//"1.7.32"),
        new Info.GAV("redis.clients", "jedis", "")//"3.7.0")
    );
    static Map<Integer, Info.Lib>   ExtLib = new HashMap<>();       // 本地管理的JAR包
    static Map<Integer, Loader>     LibLoader = new HashMap<>();    // 类加载器

    /* LIB加载器 */
    static class Loader {
        URLClassLoader  loader;         // JAR文件名
        int             refCount;       // 依赖关系
    }

    /* 初始化 */
    static void init() throws Exception {
        // 读取配置项
        Info.Lib[] libs = Util.readJsonFile(new File(Bootstrap.ServicePath + File.separator + LIB_FILE), Info.Lib[].class);
        if ( libs != null ) {
            for (Info.Lib lib : libs) {
                if (lib == null || isSysLib(lib.groupId, lib.artifactId))
                    continue;
                ExtLib.put(lib.hashCode(), lib);
            }
        }
    }
    /* 结束 */
    static void close() {
        for ( Loader loader : LibLoader.values() )
            try { loader.loader.close(); } catch (Exception e) {}
        LibLoader.clear();
        ExtLib.clear();
    }

    // 将hash所代表的JAR包的所有依赖包的hash添加到deps中
    static void addDepends(Set<Integer> set, int hash) {
        Info.Lib lib = ExtLib.get(hash);
        if ( lib == null || lib.depends == null )
            return;
        for ( Info.GAV gav : lib.depends ) {
            if ( gav == null )
                continue;
            int hcode = gav.hashCode();
            if ( !set.contains(hcode) ) {
                set.add(hcode);
                addDepends(set, hcode);
            }
        }
    }

    // 获得jar文件
    static File getJarFile(Info.Lib lib) {
        if ( lib.jarFile == null || lib.jarFile.isEmpty() )
            return null;
        return new File(Bootstrap.ServicePath + File.separator + LIB_PATH + File.separator + lib.jarFile);
    }
    // 获得jar包的URL
    static URL getLibUrl(int hash) throws Exception {
        Info.Lib lib = ExtLib.get(hash);
        if ( lib == null )
            return null;
        File file = getJarFile(lib);
        if ( file == null || ! file.exists() )
            return null;
        return file.toURI().toURL();
    }

    /* 获得类加载器 */
    static ClassLoader getClassLoader(Info.GAV gav, File classPath) throws Exception {
        int hash = gav.hashCode();
        Loader loader = LibLoader.get(hash);
        if ( loader != null ) {
            loader.refCount ++;
            return loader.loader;
        }
        Set<Integer> depend = new HashSet<>();
        for ( Integer hcode : LibLoader.keySet() ) {
            addDepends(depend, hcode);
            if ( depend.contains(hash) ) {
                loader = LibLoader.get(hcode);
                loader.refCount ++;
                return loader.loader;
            }
            depend.clear();
        }

        // 创建一个新的URLClassLoader
        URL url = getLibUrl(hash);
        if ( url == null )
            throw new Exception("special lib not found");
        List<URL> urls = new ArrayList<>();
        if ( classPath != null )
            urls.add(classPath.toURI().toURL());
        urls.add(url);
        addDepends(depend, hash);
        for ( int hcode : depend ) {
            url = getLibUrl(hcode);
            if ( url != null )
                urls.add(url);
        }
        loader = new Loader();
        loader.loader = new URLClassLoader(urls.toArray(new URL[urls.size()]));
        loader.refCount = 1;
        LibLoader.put(hash, loader);
        return loader.loader;
    }

    /* 释放类加载器 */
    static boolean putClassLoader(ClassLoader classLoader) throws Exception {
        Integer hash = null;
        for ( Map.Entry<Integer, Loader> entry : LibLoader.entrySet() ) {
            Loader loader = entry.getValue();
            if ( classLoader != loader.loader )
                continue;
            loader.refCount --;
            if ( loader.refCount > 0 )
                return false;       // 还在被其他service/filter引用
            hash = entry.getKey();
            break;
        }
        if ( hash != null ) {
            LibLoader.remove(hash);
            try {
                ((URLClassLoader)classLoader).close();
            } catch (Exception e) {
                Bootstrap.log(LogUtil.ERROR, "close loader", e);
            }
        }
        return true;
    }

    /* 增加Lib */
    static void addLib(Info.Lib lib) {
        if ( isSysLib(lib.groupId, lib.artifactId) )
            return;
        int hash = lib.hashCode();
        if ( ExtLib.containsKey(hash) )
            return;
        ExtLib.put(hash, lib);
    }
    /* 删除Lib */
    static boolean delLib(Info.GAV gav) {
        int hash = gav.hashCode();
        Info.Lib lib = ExtLib.remove(hash);
        if ( lib == null )
            return true;

        boolean hasDepend = false;
        Set<Integer> depend = new HashSet<>();
        for ( int hcode : LibLoader.keySet() ) {
            if ( hcode == hash ) {
                hasDepend = true;
                break;
            }
            addDepends(depend, hcode);
            if ( depend.contains(hash) ) {
                hasDepend = true;
                break;
            }
            depend.clear();
        }
        if ( hasDepend ) {
            ExtLib.put(hash, lib);  // 存在依赖
            Bootstrap.log(LogUtil.INFO, "del-jar", getJarFile(lib).getName() + " still in use by another class loader");
            return false;
        }
        for ( int hcode : ExtLib.keySet() ) {
            addDepends(depend, hcode);
            if ( depend.contains(hash) ) {
                hasDepend = true;
                break;
            }
            depend.clear();
        }
        if ( hasDepend ) {
            ExtLib.put(hash, lib);  // 存在依赖
            Bootstrap.log(LogUtil.INFO, "del-jar", getJarFile(lib).getName() + " still in use by another jar");
            return false;
        }

        // 删除JAR包
        try {
            System.gc();            // 主动GC，防止由于系统缓存导致无法删除JAR包

            File file = getJarFile(lib);
            if ( file != null && file.exists() )
                if ( !file.delete() )
                    throw new Exception("delete " + file.getName() + " failure");
        } catch (Exception e) {
            Bootstrap.log(LogUtil.ERROR, "del-jar", e);
        }

        if ( lib.depends != null )
            for ( Info.GAV dep : lib.depends )
                delLib(dep);    // 删除依赖的Lib
        return true;
    }

    /** 保存Lib配置 */
    static void saveLibConfig() throws Exception {
        Util.saveJsonFile(new File(Bootstrap.ServicePath + File.separator + LIB_FILE), ExtLib.values());
    }
    /** Lib及JAR文件是否存在，-1:不存在，0:Lib存在/Jar不存在，1:Lib/Jar都存在 */
    static int hasLib(Info.GAV gav) {
        if ( isSysLib(gav.groupId, gav.artifactId) )
            return 1;
        Info.Lib lib = ExtLib.get(gav.hashCode());
        if ( lib == null )
            return -1;
        File file = getJarFile(lib);
        return file != null && file.exists() ? 1 : 0;
    }
    /** 保存上传的JAR文件 */
    static void uploadJar(String filename, long offset, byte[] data) throws Exception {
        filename = Bootstrap.ServicePath + File.separator + LIB_PATH + File.separator + filename;
        uploadFile(filename, offset, data);
    }
    /** 保存上传的文件 */
    static void uploadFile(String filename, long offset, byte[] data) throws Exception {
        File file = new File(filename);
        Util.checkFilePath(file);       // RandomAccessFile不能跨目录创建新文件
        if ( offset == 0 && file.exists() )
            file.delete();
        try (RandomAccessFile raf = new RandomAccessFile(filename, "rw")) {
            raf.seek(offset);
            raf.write(data);
        }
    }
    /** 检测是否系统依赖的Jar包，忽略版本号 */
    static boolean isSysLib(String group, String artifact) {
        Info.GAV gav = new Info.GAV(group, artifact, "");
        return SysLib.contains(gav);
    }
}
