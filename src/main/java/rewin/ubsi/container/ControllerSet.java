package rewin.ubsi.container;

import rewin.ubsi.annotation.USEntry;
import rewin.ubsi.annotation.USFilter;
import rewin.ubsi.annotation.USParam;
import rewin.ubsi.annotation.UService;
import rewin.ubsi.common.LogUtil;
import rewin.ubsi.common.Util;
import rewin.ubsi.consumer.Config;
import rewin.ubsi.consumer.Context;
import rewin.ubsi.consumer.Register;

import java.io.File;
import java.util.Map;

/**
 * UBSI容器控制器（写接口）
 */
class ControllerSet extends ControllerGet {

    @USEntry(
            tips = "关闭容器",
            readonly = false
    )
    public void shutdown(ServiceContext ctx) throws Exception {
        new Thread(new Runnable() {
            public void run() {
                try { Bootstrap.stop(); } catch (Exception e) {}
            }
        }).start();
    }

    @USEntry(
            tips = "重启容器或模块",
            params = {@USParam(name="name", tips="服务或过滤类名字，null或\"\"表示容器")},
            readonly = false,
            timeout = 3
    )
    public void restart(ServiceContext ctx, String name) throws Exception {
        if ( name == null || name.isEmpty() )
            new Thread(new Runnable() {
                public void run() {
                    try { Bootstrap.stop(); } catch (Exception e) {}
                    try {
                        Bootstrap.start();
                    } catch (Exception e) {
                        Bootstrap.log(LogUtil.ERROR, "restart", e);
                    }
                }
            }).start();
        else {
            Filter module = Bootstrap.findModule(name);
            if ( module == null )
                throw new Exception("service or filter '" + name + "' not found");
            if ( module.Status == -2 )
                return;     // 处于单例等待状态，不能"手工"启动
            int status = module.Status;
            try {
                module.stop(name);
                module.start(name);
            } finally {
                if ( status != module.Status ) {
                    Service.FlushRegister = true;
                    try { Controller.saveModuleFile(ctx); } catch (Exception e) {}
                }
            }
        }
    }

    @USEntry(
            tips = "设置服务/过滤器的状态",
            params = {@USParam(name="name", tips="服务或过滤类名字，不能为null或\"\""),
                    @USParam(name="action", tips="动作，0:stop，-1:pause，1:start")},
            result = "操作是否生效",
            readonly = false
    )
    public boolean setStatus(ServiceContext ctx, String name, int action) throws Exception {
        if ( name == null || name.isEmpty() )
            return false;
        Filter module = Bootstrap.findModule(name);
        if ( module == null )
            return false;
        if ( module.Status == action )
            return false;

        boolean res = false;
        if ( action == 0 ) {
            if ( module.Status == 1 && WorkHandler.isDealing(name) )
                return false;               // 正常运行且有正在处理的任务，则不能停止
            res = module.stop(name);
        } else if ( action < 0 )
            res = module.pause(true);
        else {
            if ( module.Status == -2 )
                return false;               // 处于单例等待状态，不能"手工"启动
            if ( module.Status == -1 )
                res = module.pause(false);  // 取消暂停
            else
                res = module.start(name);
        }
        if ( res ) {
            Service.FlushRegister = true;
            Controller.saveModuleFile(ctx);
        }
        return res;
    }

    @USEntry(
            tips = "设置服务依赖",
            params = {@USParam(name="name", tips="服务或过滤类名字，不能为null或\"\""),
                      @USParam(name="json", tips="Map<String,Info.Depend>结构的json字符串")},
            result = "操作是否生效",
            readonly = false
    )
    public boolean setDepend(ServiceContext ctx, String name, String json) throws Exception {
        if ( name == null || name.isEmpty() )
            return false;

        Map<String, Info.Depend> idep = null;
        if ( json != null )
            idep = Util.json2Type(json, Map.class, String.class, Info.Depend.class);

        Map<String, Filter.Depend> fdep = null;
        Filter module = Bootstrap.ServiceMap.get(name);
        if ( module != null ) {
            UService us = (UService) module.JClass.getAnnotation(UService.class);
            fdep = module.loadDepend(us.depend());
        } else {
            module = Bootstrap.findFilter(name);
            if (module != null) {
                USFilter us = (USFilter) module.JClass.getAnnotation(USFilter.class);
                fdep = module.loadDepend(us.depend());
            }
        }

        if ( module == null || fdep == null )
            return false;
        module.addDepend(fdep, idep);
        module.Dependency = fdep;
        Controller.saveModuleFile(ctx);
        return true;
    }

    @USEntry(
            tips = "设置访问控制策略",
            params = {@USParam(name="json", tips="Info.AclTable结构的json字符串")},
            readonly = false
    )
    public void setAcl(ServiceContext ctx, String json) throws Exception {
        Info.AclTable aclTable = json == null ? null : Util.json2Type(json, Info.AclTable.class);
        ServiceAcl.setAcl(aclTable);
        ctx.saveDataFile(Controller.ACL_FILE, aclTable);
    }

    @USEntry(
            tips = "设置本地路由策略",
            params = {@USParam(name="json", tips="Register.Router[]结构的json字符串")},
            readonly = false
    )
    public void setRouter(ServiceContext ctx, String json) throws Exception {
        Register.Router[] routers = json == null ? null : Util.json2Type(json, Register.Router[].class);
        Context.setRouteTable(routers);
    }

    @USEntry(
            tips = "设置接口仿真数据",
            params = {@USParam(name="service", tips="服务名字，不能为null或\"\""),
                    @USParam(name="entry", tips="接口名字，不能为null或\"\""),
                    @USParam(name="data", tips="仿真数据")},
            readonly = false
    )
    public void setMockData(ServiceContext ctx, String service, String entry, Object data) throws Exception {
        Context.setMockData(service, entry, data);
    }

    @USEntry(
            tips = "清除接口仿真数据",
            params = {@USParam(name="service", tips="服务名字，null表示全部"),
                    @USParam(name="entry", tips="接口名字，null表示全部")},
            readonly = false
    )
    public void setMockClear(ServiceContext ctx, String service, String entry) throws Exception {
        Context.clearMockData(service, entry);
    }

    @USEntry(
            tips = "设置配置参数",
            params = {@USParam(name="name", tips="服务/过滤类名字，不能为null，\"\"表示控制器"),
                    @USParam(name="json", tips="JSON格式的配置项信息")},
            readonly = false
    )
    public void setConfig(ServiceContext ctx, String name, String json) throws Exception {
        Filter module = Bootstrap.findModule(name);
        if ( module == null )
            throw new Exception("service or filter '" + name + "' not found");
        if (module.EntryConfigSet != null)
            module.EntryConfigSet.invoke(null, new ServiceContext(name), json);     // 微服务的配置数据
    }

    @USEntry(
            tips = "设置日志参数",
            params = {@USParam(name="json", tips="Config.Log结构的json字符串")},
            readonly = false
    )
    public void setLogConfig(ServiceContext ctx, String json) throws Exception {
        Config.Log logs = json == null ? null : Util.json2Type(json, Config.Log.class);
        Context.setLogConfig(logs);
        Bootstrap.LogForce = logs.container;
    }

    @USEntry(
            tips = "上传资源文件",
            params = {@USParam(name="name", tips="微服务/过滤器名字，不能为null或\"\""),
                    @USParam(name="dir", tips="目录名字，可以为null或\"\""),
                    @USParam(name="filename", tips="资源文件名"),
                    @USParam(name="offset", tips="文件的写入位置(偏移量)"),
                    @USParam(name="data", tips="文件的写入数据")},
            readonly = false
    )
    public void putResourceFile(ServiceContext ctx, String name, String dir, String filename, long offset, byte[] data) throws Exception {
        String path = ServiceContext.getLocalPath(name, dir);
        LibManager.uploadFile(path + filename, offset, data);
    }

}
