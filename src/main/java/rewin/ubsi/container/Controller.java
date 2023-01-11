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

package rewin.ubsi.container;

import rewin.ubsi.annotation.*;
import rewin.ubsi.common.LogUtil;
import rewin.ubsi.common.Util;
import rewin.ubsi.consumer.Config;
import rewin.ubsi.consumer.Context;

import java.util.*;

/**
 * UBSI容器控制器（静态接口）
 */
@UService(
        name = "",
        tips = "UBSI微服务容器控制器",
        version = "2.3.2",                      // 容器的版本号
        release = false                         // 容器的发行状态（false表示community-edition）
)
class Controller extends ControllerModule {

    final static String CONFIG_FILE = "rewin.ubsi.container.json";
    final static String MODULE_FILE = "rewin.ubsi.module.json";
    final static String ACL_FILE = "rewin.ubsi.acl.json";

    static class Module {
        public String                   class_name;         // Java类名字
        public Info.GAV                 jar_lib;            // 依赖的JAR包
        public boolean                  startup = false;    // 是否启动
        public Map<String, Info.Depend> depends;            // 服务依赖
    }
    /* 本地安装的模块定义 */
    static class Modules {
        public  Map<String, Module> services;   // 服务
        public  Module[]            filters;    // 过滤器
    }

    /* 检查是否重名 */
    static boolean checkRepeat(String name) {
        for ( String sname : Bootstrap.ServiceMap.keySet() ) {
            if (sname.equals(name))
                return true;
            Service srv = Bootstrap.ServiceMap.get(sname);
            if ( srv != null && srv.JClass.getName().equals(name) )
                return true;
        }
        if ( Bootstrap.findFilter(name) != null )
            return true;
        return false;
    }

    /* 复制依赖关系 */
    static Map<String, Info.Depend> cloneDepend(Map<String, Filter.Depend> depends) {
        if ( depends != null && !depends.isEmpty() ) {
            Map<String, Info.Depend> res = new HashMap<>();
            for ( Map.Entry<String, Filter.Depend> entry : depends.entrySet() ) {
                Filter.Depend fdep = entry.getValue();
                Info.Depend idep = new Info.Depend();
                idep.release = fdep.Release;
                idep.version_min = Util.getVersion(fdep.VerMin);
                idep.version_max = Util.getVersion(fdep.VerMax);
                res.put(entry.getKey(), idep);
            }
            return res;
        }
        return null;
    }

    /* 生成并保存模块配置文件 */
    static void saveModuleFile(ServiceContext ctx) throws Exception {
        Modules modules = new Modules();
        modules.services = new HashMap<>();
        for ( String sname : Bootstrap.ServiceMap.keySet() ) {
            if ( sname.isEmpty() )
                continue;       // 控制器
            Service srv = Bootstrap.ServiceMap.get(sname);
            if ( srv == null )
                continue;
            Module module = new Module();
            module.jar_lib = srv.JarLib;
            module.class_name = srv.JClass.getName();
            module.startup = srv.Status == 1 || srv.Status == -2;
            module.depends = cloneDepend(srv.Dependency);
            modules.services.put(sname, module);
        }
        List<Module> filters = new ArrayList<>();
        Iterator<Filter> iter = Bootstrap.FilterList.iterator();
        while ( iter.hasNext() ) {
            Filter flt = iter.next();
            Module module = new Module();
            module.jar_lib = flt.JarLib;
            module.class_name = flt.JClass.getName();
            module.startup = flt.Status == 1 || flt.Status == -2;
            module.depends = cloneDepend(flt.Dependency);
            filters.add(module);
        }
        if ( !filters.isEmpty() )
            modules.filters = filters.toArray(new Module[filters.size()]);
        ctx.saveDataFile(MODULE_FILE, modules);
    }

    @USInit
    public static void init(ServiceContext ctx) throws Exception {
        Bootstrap.Host = null;
        Bootstrap.Port = 0;
        // 读取运行配置文件
        Info.Container config = ctx.readDataFile(CONFIG_FILE, Info.Container.class);
        if ( config != null ) {
            checkConfig(config);
            if ( config.host != null )
                Bootstrap.Host = config.host.toLowerCase();
            Bootstrap.Port = config.port;
            Bootstrap.BackLog = config.backlog;
            Bootstrap.IOThreads = config.io_threads;
            Bootstrap.WorkThreads = config.work_threads;
            Bootstrap.TimeoutFuse = config.timeout_fuse;
            Bootstrap.Overload = config.overload;
            Bootstrap.Forward = config.forward;
            Bootstrap.ForwardDoor = config.forward_door;
        }
        if ( Bootstrap.Host == null )
            Bootstrap.Host = Bootstrap.resolveHost();
        if ( Bootstrap.Port == 0 )
            Bootstrap.Port = Bootstrap.resolvePort(Bootstrap.DEFAULT_PORT);

        Context.setLogApp(Bootstrap.Host + "#" + Bootstrap.Port, Bootstrap.LOG_APPTAG);

        // 读取模块配置文件
        Modules modules = ctx.readDataFile(MODULE_FILE, Modules.class);
        if ( modules != null ) {
            if ( modules.services != null ) {
                for ( String sname : modules.services.keySet() ) {
                    if ( sname == null || sname.trim().isEmpty() ) {
                        Bootstrap.log(LogUtil.ERROR, "start controller", "service name is empty");
                        continue;
                    }
                    Module module = modules.services.get(sname);
                    sname = sname.trim();
                    if ( checkRepeat(sname) ) {
                        Bootstrap.log(LogUtil.ERROR, "start controller", "service name \"" + sname + "\" repeated");
                        continue;
                    }
                    if ( module.class_name == null || module.class_name.trim().isEmpty() ) {
                        Bootstrap.log(LogUtil.ERROR, "start controller", "service class name \"" + module.class_name + "\" is empty");
                        continue;
                    }
                    module.class_name = module.class_name.trim();
                    if ( checkRepeat(module.class_name) ) {
                        Bootstrap.log(LogUtil.ERROR, "start controller", "service class name \"" + module.class_name + "\" repeated");
                        continue;
                    }
                    try {
                        Service srv = Service.load(module.class_name, module.jar_lib, sname);
                        srv.addDepend(srv.Dependency, module.depends);
                        Bootstrap.ServiceMap.put(sname, srv);
                        if (module.startup)
                            srv.start(sname);
                    } catch (Exception e) {
                        Bootstrap.log(LogUtil.ERROR, "start " + sname, e);
                        continue;
                    }
                }
            }
            if ( modules.filters != null ) {
                for ( Module module : modules.filters ) {
                    if ( module.class_name == null || module.class_name.trim().isEmpty() ) {
                        Bootstrap.log(LogUtil.ERROR, "start controller", "filter class name is empty");
                        continue;
                    }
                    module.class_name = module.class_name.trim();
                    if ( checkRepeat(module.class_name) ) {
                        Bootstrap.log(LogUtil.ERROR, "start controller", "filter class name \"" + module.class_name + "\" repeated");
                        continue;
                    }
                    try {
                        Filter flt = Filter.load(module.class_name, module.jar_lib);
                        flt.addDepend(flt.Dependency, module.depends);
                        Bootstrap.FilterList.add(flt);
                        if (module.startup)
                            flt.start(module.class_name);
                    } catch (Exception e) {
                        Bootstrap.log(LogUtil.ERROR, "start " + module.class_name, e);
                        continue;
                    }
                }
            }
        }

        // 读取acl配置文件
        Info.AclTable aclTable = ctx.readDataFile(ACL_FILE, Info.AclTable.class);
        if ( aclTable != null )
            ServiceAcl.setAcl(aclTable);

        // 读取日志配置文件
        Config.Log logs = ctx.readDataFile(Context.LOG_FILE, Config.Log.class);
        if ( logs != null )
            Bootstrap.LogForce = logs.container;
    }

    @USClose
    public static void close(ServiceContext ctx) throws Exception {
        for ( String sname : Bootstrap.ServiceMap.keySet() ) {
            if (sname.isEmpty())
                continue;   // Controller
            Service srv = Bootstrap.ServiceMap.get(sname);
            if ( srv != null )
                try { srv.stop(sname); } catch (Exception e) {}
        }
        Bootstrap.ServiceMap.clear();

        Iterator<Filter> iter = Bootstrap.FilterList.iterator();
        while ( iter.hasNext() ) {
            Filter filter = iter.next();
            try { filter.stop(filter.JClass.getName()); } catch (Exception e) {}
        }
        Bootstrap.FilterList.clear();
    }

    @USInfo
    public static Info.Controller info(ServiceContext ctx) throws Exception {
        return Info.getController(new Info.Controller());
    }

    @USConfigGet
    public static Object getConfig(ServiceContext ctx) throws Exception {
        Info.AllConfig cfg = new Info.AllConfig();
        cfg.container = new Info.Container();
        cfg.container_restart = ctx.readDataFile(CONFIG_FILE, Info.Container.class);
        cfg.container_comment = new Info.ContainerComment();
        Config consumer = Context.getConfig();
        cfg.consumer = consumer.consumer;
        cfg.consumer_restart = consumer.consumer_restart;
        cfg.consumer_comment = consumer.consumer_comment;
        return cfg;
    }

    /* 检查配置项的值 */
    static void checkConfig(Info.Container config) throws Exception {
        config.host = Util.checkEmpty(config.host);
        if ( config.port != 0 && (config.port <= 256 || config.port >= 65536) )
            throw new Exception("invalid port");
        config.backlog = Util.checkMinMax(config.backlog, Bootstrap.MIN_BACKLOG, Bootstrap.MAX_BACKLOG);
        config.io_threads = Util.checkMinMax(config.io_threads, Bootstrap.MIN_IOTHREADS, Bootstrap.MAX_IOTHREADS);
        config.work_threads = Util.checkMinMax(config.work_threads, Bootstrap.MIN_WORKTHREADS, Bootstrap.MAX_WORKTHREADS);
        config.timeout_fuse = Util.checkMinMax(config.timeout_fuse, 0, (config.work_threads + 1) / 2);
        config.overload = Util.checkMinMax(config.overload, Bootstrap.MIN_OVERLOAD, Bootstrap.MAX_OVERLOAD);
        config.forward = Util.checkMinMax(config.forward, Bootstrap.MIN_FORWARD, Bootstrap.MAX_FORWARD);
    }

    @USConfigSet
    public static void setConfig(ServiceContext ctx, String json) throws Exception {
        Info.AllConfig cfg = Util.json2Type(json, Info.AllConfig.class);
        if ( cfg.container != null ) {
            checkConfig(cfg.container);
            ctx.saveDataFile(CONFIG_FILE, cfg.container);
            Bootstrap.TimeoutFuse = cfg.container.timeout_fuse;
            Bootstrap.Overload = cfg.container.overload;
            Bootstrap.Forward = cfg.container.forward;
            Bootstrap.ForwardDoor = cfg.container.forward_door;
            Service.FlushRegister = true;       // 刷新服务注册表
        }
        if ( cfg.consumer != null )
            Context.setConfig(cfg.consumer);
    }

}
