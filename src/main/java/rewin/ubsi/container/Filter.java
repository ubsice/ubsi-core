package rewin.ubsi.container;

import rewin.ubsi.annotation.*;
import rewin.ubsi.common.Util;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

import static rewin.ubsi.container.LibManager.LIB_PATH;

/**
 * 微服务过滤器定义
 */
class Filter {

    /** 依赖关系定义 */
    static class Depend {
        int     VerMin;             // 最小版本
        int     VerMax;             // 最大版本
        int     Release;            // 是否正式版本
    }

    Info.GAV    JarLib;             // 依赖的JAR包
    Class       JClass;             // 类
    String      Tips;               // 说明
    int         Version;            // 版本
    boolean     Release;            // 是否发行版
    Map<String, Depend> Dependency; // 依赖关系

    Method      EntryBefore;        // 前置接口
    int         TimeoutBefore;      // 前置接口的超时时间
    Method      EntryAfter;         // 后置接口
    int         TimeoutAfter;       // 后置接口的超时时间
    Method      EntryInit;          // 初始化接口
    Method      EntryClose;         // 关闭服务接口
    Method      EntryInfo;          // 运行信息查询接口
    Method      EntryConfigGet;     // 读取配置接口
    Method      EntryConfigSet;     // 更改配置接口

    volatile int Status = 0;        // 状态
    int         TimeStatus = 0;     // 状态的时间戳

    /** 启动 */
    synchronized boolean start(String name) throws Exception {
        if ( Status != 0 && Status != -2 )
            return false;
        if ( EntryInit != null ) {
            ServiceContext ctx = new ServiceContext(name);
            try {
                EntryInit.invoke(null, ctx);
            } catch (Exception e) {
                if ( EntryClose != null )
                    try { EntryClose.invoke(null, ctx); } catch (Exception ee) {}
                throw e;
            }
        }
        TimeStatus = (int)(System.currentTimeMillis() / 1000);     // 状态时间
        Status = 1;
        return true;
    }

    /** 关闭 */
    synchronized boolean stop(String name) throws Exception {
        if ( Status == 0 )
            return false;
        int status = Status;
        TimeStatus = (int)(System.currentTimeMillis() / 1000);     // 状态时间
        Status = 0;
        if ( status == -2 )
            return true;
        if ( EntryClose != null )
            EntryClose.invoke(null, new ServiceContext(name));
        return true;
    }

    /** 暂停 */
    synchronized boolean pause(boolean yes) {
        if ( yes && Status != 1 )
            return false;
        if ( !yes && Status != -1 )
            return false;
        TimeStatus = (int)(System.currentTimeMillis() / 1000);     // 状态时间
        Status = yes ? -1 : 1;
        return true;
    }

    /** 加载依赖项 */
    Map<String, Depend> loadDepend(USDepend[] depends) {
        Map<String, Depend> map = new HashMap<>();
        for (USDepend depend : depends ) {
            Depend dep = new Depend();
            dep.VerMin = Util.getVersion(depend.version());
            dep.Release = depend.release() ? 1 : -1;
            map.put(depend.name(), dep);
        }
        return map;
    }

    /** 添加依赖项 */
    void addDepend(Map<String, Depend> res, Map<String, Info.Depend> depends) {
        if ( depends == null )
            return;
        for ( String sname : depends.keySet() ) {
            if ( sname == null || sname.trim().isEmpty() )
                continue;
            Info.Depend dep = depends.get(sname);
            sname = sname.trim();
            Depend depend = res.get(sname);
            if ( depend == null ) {
                depend = new Depend();
                depend.VerMin = Util.getVersion(dep.version_min);
                depend.VerMax = Util.getVersion(dep.version_max);
                depend.Release = dep.release < 0 ? -1 : dep.release;
                res.put(sname, depend);
            } else {
                int ver = Util.getVersion(dep.version_min);
                if ( ver > depend.VerMin )
                    depend.VerMin = ver;
                ver = Util.getVersion(dep.version_max);
                if ( ver > 0 )
                    depend.VerMax = ver;
                if ( dep.release > -2 )
                    depend.Release = dep.release;
            }
        }
    }

    /** 检查接口 */
    void checkMethod(Method method) throws Exception {
        if ( ! Modifier.isStatic(method.getModifiers()) ) {
            if ( method.isAnnotationPresent(USBefore.class) ) {
                if ( EntryBefore != null )
                    throw new Exception(JClass.getName() + " has too many @USBefore");
                EntryBefore = method;
                USBefore usBefore = (USBefore) method.getAnnotation(USBefore.class);
                TimeoutBefore = usBefore.timeout();
                if ( TimeoutBefore < 1 )
                    TimeoutBefore = 1;
            } else if ( method.isAnnotationPresent(USAfter.class) ) {
                if ( EntryAfter != null )
                    throw new Exception(JClass.getName() + " has too many @USAfter");
                EntryAfter = method;
                USAfter usAfter = (USAfter) method.getAnnotation(USAfter.class);
                TimeoutAfter = usAfter.timeout();
                if ( TimeoutAfter < 1 )
                    TimeoutAfter = 1;
            }
        } else if ( method.isAnnotationPresent(USInit.class) ) {
            if ( EntryInit != null )
                throw new Exception(JClass.getName() + " has too many @USInit");
            EntryInit = method;
        } else if ( method.isAnnotationPresent(USClose.class) ) {
            if ( EntryClose != null )
                throw new Exception(JClass.getName() + " has too many @USClose");
            EntryClose = method;
        } else if ( method.isAnnotationPresent(USInfo.class) ) {
            if ( EntryInfo != null )
                throw new Exception(JClass.getName() + " has too many @USInfo");
            EntryInfo = method;
        } else if ( method.isAnnotationPresent(USConfigGet.class) ) {
            if ( EntryConfigGet != null )
                throw new Exception(JClass.getName() + " has too many @USConfigGet");
            EntryConfigGet = method;
        } else if ( method.isAnnotationPresent(USConfigSet.class) ) {
            if ( EntryConfigSet != null )
                throw new Exception(JClass.getName() + " has too many @USConfigSet");
            EntryConfigSet = method;
        }
    }

    /////////////////////////////////////////////////////////////////////////

    /* 检测容器的版本号 */
    static void checkContainer(String ver) throws Exception {
        ver = Util.checkEmpty(ver);
        if ( ver == null )
            return;
        if ( Util.getVersion(ver) > Bootstrap.ServiceMap.get("").Version )
            throw new Exception("container's version is too low, need " + ver + " or later");
    }
    /* 处理需要加载到SystemClassLoader中的Jar包 */
    static void loadSystemLibs(String[] jars) throws Exception {
        if ( jars.length == 0 )
            return;
        Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
        if ( !method.isAccessible() )
            method.setAccessible(true);
        URLClassLoader classLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
        for ( String jar : jars ) {
            String jarfile = Bootstrap.ServicePath + File.separator + LIB_PATH + File.separator + jar;
            URL url = new File(jarfile).toURI().toURL();
            method.invoke(classLoader, url);
        }
    }

    /** 加载@USFilter类 */
    static Filter load(String className, Info.GAV gav) throws Exception {
        Filter filter = new Filter();
        if ( gav == null )
            filter.JClass = Class.forName(className);
        else {
            File classPath = new File(Bootstrap.ServicePath + File.separator + Bootstrap.MODULE_PATH + File.separator + className);
            filter.JClass = LibManager.getClassLoader(gav, classPath).loadClass(className);
        }
        try {
            if ( ! filter.JClass.isAnnotationPresent(USFilter.class) )
                throw new Exception(className + " is not a UBSI Filter");
            filter.JClass.newInstance();     // 测试是否能正常生成对象
            filter.JarLib = gav;
            USFilter us = (USFilter) filter.JClass.getAnnotation(USFilter.class);
            filter.Tips = us.tips();
            filter.Version = Util.getVersion(us.version());
            filter.Release = us.release();
            filter.Dependency = filter.loadDepend(us.depend());
            for ( Method method : filter.JClass.getMethods() )
                filter.checkMethod(method);
            checkContainer(us.container());
            loadSystemLibs(us.syslib());
        } catch (Exception e) {
            if ( gav != null )
                LibManager.putClassLoader(filter.JClass.getClassLoader());
            throw e;
        }
        filter.TimeStatus = (int)(System.currentTimeMillis() / 1000);     // 状态时间
        filter.Status = 0;
        return filter;
    }

}
