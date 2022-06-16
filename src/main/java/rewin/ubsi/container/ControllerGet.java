package rewin.ubsi.container;

import rewin.ubsi.annotation.*;
import rewin.ubsi.common.JedisUtil;
import rewin.ubsi.common.Util;
import rewin.ubsi.consumer.Config;
import rewin.ubsi.consumer.Context;
import rewin.ubsi.consumer.Register;

import java.io.File;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * UBSI容器控制器（读接口）
 */
class ControllerGet {
    @USEntry(
            tips = "获得运行信息",
            params = {@USParam(name="name", tips="服务或过滤类名字，null表示容器节点，\"\"表示控制器")},
            result = "service为null返回Info.Runtime，service为\"\"返回Info.Controller，否则由微服务的@info接口返回"
    )
    public Object getRuntime(ServiceContext ctx, String name) throws Exception {
        if ( name == null ) {
            Set<String>[] timeouts = WorkHandler.getTimeoutDeal();
            Info.Runtime res = new Info.Runtime();
            res.client_connection = (int)(Bootstrap.SocketConnected.get() - Bootstrap.SocketDisconnect.get());
            res.request_overload = Bootstrap.RequestOverload.get();
            res.request_over = Bootstrap.RequestOver.get();
            res.request_forward = Bootstrap.RequestForward.get();
            res.request_dealing = (int)(Bootstrap.RequestDeal.get() - Bootstrap.RequestOver.get());
            res.request_waiting = (int)(Bootstrap.RequestTotal.get() - Bootstrap.RequestDeal.get());
            res.redis_enable = JedisUtil.isInited();
            if ( res.redis_enable ) {
                int[] count = JedisUtil.getPools();
                res.redis_conn_active = count[0];
                res.redis_conn_idle = count[1];
            } else {
                res.redis_conn_active = 0;
                res.redis_conn_idle = 0;
            }
            for ( String sname : Bootstrap.ServiceMap.keySet() ) {
                Service srv = Bootstrap.ServiceMap.get(sname);
                if ( srv == null )
                    continue;
                Info.SRuntime ms = new Info.SRuntime();
                ms.class_name = srv.JClass.getName();
                ms.tips = srv.Tips;
                ms.version = Util.getVersion(srv.Version);
                ms.release = srv.Release;
                ms.status = srv.Status;
                ms.time_status = srv.TimeStatus;
                ms.request_over = srv.RequestOver.get();
                ms.request_error = srv.RequestError.get();
                ms.request_dealing = (int)(srv.RequestDeal.get() - srv.RequestOver.get());
                ms.dealing_timeout = timeouts[0].contains(sname);
                res.services.put(sname, ms);
            }
            if ( !Bootstrap.FilterList.isEmpty() ) {
                res.filters = new ArrayList<>();
                Iterator<Filter> iter = Bootstrap.FilterList.iterator();
                while (iter.hasNext()) {
                    Filter filter = iter.next();
                    Info.FRuntime fr = new Info.FRuntime();
                    fr.class_name = filter.JClass.getName();
                    fr.tips = filter.Tips;
                    fr.version = Util.getVersion(filter.Version);
                    fr.release = filter.Release;
                    fr.status = filter.Status;
                    fr.time_status = filter.TimeStatus;
                    fr.dealing_timeout = timeouts[1].contains(fr.class_name);
                    res.filters.add(fr);
                }
            }
            return res;
        }

        // 指定名字
        Filter module = Bootstrap.findModule(name);
        if ( module == null )
            throw new Exception("service or filter '" + name + "' not found");
        if ( module.EntryInfo == null )
            return null;
        return module.EntryInfo.invoke(null, new ServiceContext(name));  // 过滤类的运行信息
    }

    @USEntry(
            tips = "获得正在处理的请求",
            params = {@USParam(name="service", tips="服务名字，null表示全部，\"\"表示控制器")},
            result = "正在处理的请求列表"
    )
    public List<Info.Deal> getDealing(ServiceContext ctx, String service) throws Exception {
        List<Info.Deal> res = new ArrayList<>();
        for ( WorkHandler.Deal deal : WorkHandler.Dealing.values() ) {
            if (service != null && !service.equals(deal.Service))
                continue;
            Info.Deal vd = new Info.Deal();
            long nano = System.nanoTime();
            synchronized (deal) {
                vd.time_all = nano - deal.StartTime;
                if (deal.Timeout != 0) {
                    vd.time_deal = nano - deal.DealTime;
                    vd.timeout = deal.Timeout;
                    vd.filter = deal.InFilter;
                    vd.interceptor = deal.Interceptor;
                }
                vd.service = deal.Service;
                vd.entry = deal.Entry;
                vd.client = deal.Client;
            }
            res.add(vd);
        }
        return res;
    }

    @USEntry(
            tips = "获得服务的接口",
            params = {@USParam(name="service", tips="服务名字，不能为null，\"\"表示控制器")},
            result = "指定服务的接口定义列表"
    )
    public List<Info.Entry> getEntry(ServiceContext ctx, String service) throws Exception {
        Service srv = Bootstrap.ServiceMap.get(service);
        if ( srv == null )
            throw new Exception("service '" + service + "' not found");
        List<Info.Entry> list = new ArrayList<>();
        for ( Service.Entry entry : srv.EntryMap.values() ) {
            Info.Entry en = new Info.Entry();
            en.load(entry.JMethod, entry.JAnnotation);
            en.dealing = (int)(entry.RequestDeal.get() - entry.RequestOver.get());
            en.deal_over = entry.RequestOver.get();
            en.deal_error = entry.RequestError.get();
            en.max_time = entry.RequestTime.get();
            en.req_id = entry.RequestID;
            list.add(en);
        }
        return list;
    }

    @USEntry(
            tips = "获得服务依赖",
            params = {@USParam(name="name", tips="服务/过滤类名字，不能为null，\"\"表示控制器")},
            result = "依赖的服务"
    )
    public Map<String, Info.Depend> getDepend(ServiceContext ctx, String name) throws Exception {
        Filter module = Bootstrap.findModule(name);
        if ( module == null )
            throw new Exception("service or filter '" + name + "' not found");
        Map<String, Info.Depend> res = new HashMap<>();
        Map<String, Filter.Depend> depends = module.Dependency;     // 防止变量重置
        for ( String sname : depends.keySet() ) {
            Filter.Depend fdep = depends.get(sname);
            Info.Depend idep = new Info.Depend();
            idep.version_min = Util.getVersion(fdep.VerMin);
            idep.version_max = Util.getVersion(fdep.VerMax);
            idep.release = fdep.Release;
            res.put(sname, idep);
        }
        return res;
    }

    @USEntry(
            tips = "获得访问权限设置",
            result = "容器的访问权限"
    )
    public Info.AclTable getAcl(ServiceContext ctx) throws Exception {
        Info.AclTable aclTable = ctx.readDataFile(Controller.ACL_FILE, Info.AclTable.class);
        if ( aclTable == null ) {
            aclTable = new Info.AclTable();
            aclTable.default_auth = "" + ((ServiceAcl.AclPolicy & 0x01) > 0 ? "r" : "") + ((ServiceAcl.AclPolicy & 0x02) > 0 ? "w" : "");
        }
        return aclTable;
    }

    @USEntry(
            tips = "获得路由配置",
            result = "本地路由表"
    )
    public Register.Router[] getRouter(ServiceContext ctx) throws Exception {
        return Context.getRouteTable();
    }

    @USEntry(
            tips = "获得有仿真数据的服务接口",
            params = {@USParam(name="service", tips="服务名字，null表示全部")},
            result = "服务:接口列表"
    )
    public Map<String, Set<String>> getMockedEntry(ServiceContext ctx, String service) throws Exception {
        return Context.getMockedEntry(service);
    }

    @USEntry(
            tips = "获得接口仿真数据",
            params = {@USParam(name="service", tips="服务名字，不能为null或\"\""),
                    @USParam(name="entry", tips="接口名字，不能为null或\"\"")},
            result = "仿真数据"
    )
    public Object getMockData(ServiceContext ctx, String service, String entry) throws Exception {
        return Context.getMockData(service, entry);
    }

    @USEntry(
            tips = "获得配置数据",
            params = {@USParam(name="name", tips="服务/过滤类名字，不能为null，\"\"表示控制器")},
            result = "配置数据"
    )
    public Object getConfig(ServiceContext ctx, String name) throws Exception {
        Filter module = Bootstrap.findModule(name);
        if ( module != null ) {
            if ( module.EntryConfigGet == null )
                return null;
            return module.EntryConfigGet.invoke(null, new ServiceContext(name));     // 微服务的配置数据
        }
        throw new Exception("service or filter '" + name + "' not found");
    }

    @USEntry(
            tips = "获得日志设置",
            result = "日志参数配置"
    )
    public Config.Log getLogConfig(ServiceContext ctx) throws Exception {
        Config.Log logs = Context.getLogConfig();
        logs.container = Bootstrap.LogForce;
        return logs;
    }
    @USEntry(
            tips = "获得日志文件列表",
            params = {@USParam(name="dir", tips="目录名字，可以为null或\"\"")},
            result = "日志文件列表"
    )
    public List<Config.LogFile> getLogFileList(ServiceContext ctx, String dir) throws Exception {
        return Context.getLogFileList(dir);
    }
    @USEntry(
            tips = "获得日志文件",
            params = {@USParam(name="dir", tips="目录名字，可以为null或\"\""),
                    @USParam(name="filename", tips="文件名字"),
                    @USParam(name="offset", tips="起始位置"),
                    @USParam(name="length", tips="数据长度")},
            result = "[long, byte[]] ~ [总数据长度, 日志文件内容(utf-8编码)]"
    )
    public Object[] getLogFile(ServiceContext ctx, String dir, String filename, long offset, int length) throws Exception {
        return Context.getLogFile(dir, filename, offset, length);
    }

    @USEntry(
            tips = "获得资源文件列表",
            params = {@USParam(name="name", tips="微服务/过滤器名字，不能为null或\"\""),
                    @USParam(name="dir", tips="目录名字，可以为null或\"\"")},
            result = "资源文件列表"
    )
    public List<Config.LogFile> getResourceFileList(ServiceContext ctx, String name, String dir) throws Exception {
        String path = ServiceContext.getLocalPath(name, dir);
        return Config.getFileList(new File(path));
    }
    @USEntry(
            tips = "获得资源文件",
            params = {@USParam(name="name", tips="微服务/过滤器名字，不能为null或\"\""),
                    @USParam(name="dir", tips="目录名字，可以为null或\"\""),
                    @USParam(name="filename", tips="文件名字"),
                    @USParam(name="offset", tips="起始位置"),
                    @USParam(name="length", tips="数据长度")},
            result = "[long, byte[]] ~ [总数据长度, 文件内容]"
    )
    public Object[] getResourceFile(ServiceContext ctx, String name, String dir, String filename, long offset, int length) throws Exception {
        String path = ServiceContext.getLocalPath(name, dir);
        return Config.readFile(new File(path + filename), offset, length);
    }
}
