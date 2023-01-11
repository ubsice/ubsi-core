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

import rewin.ubsi.annotation.USEntry;
import rewin.ubsi.annotation.USParam;
import rewin.ubsi.cli.Request;
import rewin.ubsi.common.LogUtil;
import rewin.ubsi.common.Util;

import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * UBSI容器控制器（模块管理）
 */
class ControllerModule extends ControllerSet {

    @USEntry(
            tips = "是否安装了JAR包",
            params = {@USParam(name="groupId", tips="Jar包的groupId"),
                    @USParam(name="artifactId", tips="Jar包的artifactId"),
                    @USParam(name="version", tips="Jar包的version")},
            result = "-1:未安装，0:已注册但文件未上传，1:已安装"
    )
    public int hasJar(ServiceContext ctx, String groupId, String artifactId, String version) {
        return LibManager.hasLib(new Info.GAV(groupId, artifactId, version));
    }

    @USEntry(
            tips = "上传JAR包",
            params = {@USParam(name="filename", tips="Jar包的文件名"),
                    @USParam(name="offset", tips="文件的写入位置(偏移量)"),
                    @USParam(name="data", tips="文件的写入数据")},
            readonly = false
    )
    public void uploadJar(ServiceContext ctx, String filename, long offset, byte[] data) throws Exception {
        LibManager.uploadJar(filename, offset, data);
    }

    @USEntry(
            tips = "注册JAR包",
            params = {@USParam(name="groupId", tips="Jar包的groupId，不能为空"),
                    @USParam(name="artifactId", tips="Jar包的artifactId，不能为空"),
                    @USParam(name="version", tips="Jar包的version，不能为空"),
                    @USParam(name="filename", tips="Jar包的文件名，不能为空"),
                    @USParam(name="depends", tips="依赖的Jar包，格式:" +
                            "[[groupId,artifactId,version], ...]，可以为空")},
            readonly = false
    )
    public void registerJar(ServiceContext ctx, String groupId, String artifactId, String version, String filename, Object[] depends) throws Exception {
        Info.Lib lib = new Info.Lib();
        lib.groupId = groupId;
        lib.artifactId = artifactId;
        lib.version = version;
        lib.jarFile = filename;
        Set<Info.GAV> dep = new HashSet<>();
        if ( depends != null ) {
            for (Object gav : depends)
                dep.add(new Info.GAV((String) ((Object[]) gav)[0], (String) ((Object[]) gav)[1], (String) ((Object[]) gav)[2]));
            lib.depends = dep.toArray(new Info.GAV[dep.size()]);
        }
        LibManager.addLib(lib);
        LibManager.saveLibConfig();
    }

    @USEntry(
            tips = "注销JAR包",
            params = {@USParam(name="groupId", tips="Jar包的groupId，不能为空"),
                    @USParam(name="artifactId", tips="Jar包的artifactId，不能为空"),
                    @USParam(name="version", tips="Jar包的version，不能为空")},
            readonly = false,
            result = "1:注销成功; 0:JAR包仍在使用; -1:发生异常"
    )
    public int unregisterJar(ServiceContext ctx, String groupId, String artifactId, String version) {
        Info.GAV gav = new Info.GAV(groupId, artifactId, version);
        boolean nouse = true;
        Iterator<Filter> iter = Bootstrap.FilterList.iterator();
        while ( iter.hasNext() ) {
            Filter filter = iter.next();
            if ( filter.JarLib != null && filter.JarLib.equals(gav) ) {
                nouse = false;
                break;
            }
        }
        if ( nouse ) {
            for ( Service srv : Bootstrap.ServiceMap.values() ) {
                if ( srv.JarLib != null && srv.JarLib.equals(gav) ) {
                    nouse = false;
                    break;
                }
            }
        }
        if ( nouse ) {
            try {
                if (LibManager.delLib(gav))
                    LibManager.saveLibConfig();
            } catch (Exception e) {
                Bootstrap.log(LogUtil.ERROR, "unregisterJar " + gav.toString(), e);
                return -1;  // 发生异常
            }
            return 1;       // 注销成功
        }
        return 0;           // JAR包仍在使用
    }

    @USEntry(
            tips = "安装模块",
            params = {@USParam(name="name", tips="服务名，null表示过滤器"),
                    @USParam(name="classname", tips="service/filter的类名"),
                    @USParam(name="jarlib", tips="依赖的Jar包，格式：" +
                            "[groupId,artifactId,version]，可以为null")},
            readonly = false
    )
    public void install(ServiceContext ctx, String name, String classname, Object[] jarlib) throws Exception {
        classname = Util.checkEmpty(classname);
        if ( classname == null )
            throw new Exception("invalid classname");
        if ( Controller.checkRepeat(classname) )
            throw new Exception("classname repeated");

        Info.GAV gav = null;
        if ( jarlib != null ) {
            gav = new Info.GAV((String) jarlib[0], (String) jarlib[1], (String) jarlib[2]);
            if (LibManager.hasLib(gav) <= 0)
                throw new Exception("special jarlib not ready");
        }

        if ( name == null ) {
            // install filter
            Filter flt = Filter.load(classname, gav);
            Bootstrap.FilterList.add(flt);
        } else {
            // install service
            name = name.trim();
            if ( name.isEmpty() )
                throw new Exception("invalid service's name");
            if ( Controller.checkRepeat(name) )
                throw new Exception("service's name repeated");
            Service srv = Service.load(classname, gav, name);
            Bootstrap.ServiceMap.put(name, srv);
        }
        Service.FlushRegister = true;
        Controller.saveModuleFile(ctx);
    }

    @USEntry(
            tips = "卸载模块",
            params = {@USParam(name="name", tips="service名字或filter类名")},
            readonly = false,
            result = "0:卸载成功，1:ClassLoader仍在使用中，2:注销JAR包失败"
    )
    public int uninstall(ServiceContext ctx, String name) throws Exception {
        Filter module = Bootstrap.findModule(name);
        if ( module == null )
            throw new Exception("service or filter '" + name + "' not found");

        module.stop(name);

        if ( module instanceof Service )
            Bootstrap.ServiceMap.remove(name);
        else
            Bootstrap.FilterList.remove(module);

        Service.FlushRegister = true;       // 需要更新注册表

        int res = 0;
        if ( module.JarLib != null ) {
            if ( !LibManager.putClassLoader(module.JClass.getClassLoader()) ) {
                Bootstrap.log(LogUtil.WARN, "uninstall " + name, "class loader for " + module.JarLib.getJarFileName() + " still in use by another service/filter");
                res = 1;
            } else if ( unregisterJar(ctx, module.JarLib.groupId, module.JarLib.artifactId, module.JarLib.version) != 1 ) {
                Bootstrap.log(LogUtil.WARN, "uninstall " + name, module.JarLib.getJarFileName() + " still in use by another service/filter");
                res = 2;
            }
        }
        Controller.saveModuleFile(ctx);

        // 删除模块相关文件
        String path = Bootstrap.ServicePath + File.separator + Bootstrap.MODULE_PATH + File.separator + name;
        Util.rmdir(new File(path));
        return res;
    }
}
