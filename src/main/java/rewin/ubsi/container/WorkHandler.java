package rewin.ubsi.container;

import rewin.ubsi.common.LogUtil;
import rewin.ubsi.common.Util;
import rewin.ubsi.consumer.ErrorCode;
import rewin.ubsi.consumer.LogBody;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * UBSI请求处理
 */
class WorkHandler implements Runnable {

    static class Deal {
        public long     StartTime = System.nanoTime();      // 开始时间戳
        public String   InFilter = null;    // 过滤器
        public long     DealTime;           // 开始处理的时间戳
        public int      Interceptor = 0;    // 拦截器
        public int      Timeout = 0;        // 超时设置，秒数
        public String   Service;            // 服务名字
        public String   Entry;              // 接口名字
        public String   Client;             // 客户端名字/IP
    }

    static ConcurrentMap<String, Deal>  Dealing = new ConcurrentHashMap<>();

    static Set<String>[] getTimeoutDeal() {
        Set<String>[] res = new Set[] { new HashSet<String>(), new HashSet<String>() };
        for ( Deal deal : Dealing.values() ) {
            synchronized (deal) {
                if (deal.Timeout == 0)
                    continue;
                long nano = TimeUnit.NANOSECONDS.convert(deal.Timeout, TimeUnit.SECONDS);
                if ( System.nanoTime() - deal.DealTime > nano)
                    res[deal.InFilter == null ? 0 : 1].add(deal.InFilter == null ? deal.Service : deal.InFilter);
            }
        }
        return res;
    }

    static boolean isDealing(String name) {
        for ( Deal deal : Dealing.values() ) {
            synchronized (deal) {
                if ( name.equals(deal.Service) )
                    return true;
                if ( deal.InFilter != null && name.equals(deal.InFilter) )
                    return true;
            }
        }
        return false;
    }

    static void clearDealing(String name) {
        List<String> list = new ArrayList<>();
        for ( String key : Dealing.keySet() ) {
            Deal deal = Dealing.get(key);
            if ( deal != null && deal.Service.equals(name)  )
                list.add(key);
        }
        for ( String key : list )
            Dealing.remove(key);
    }

    ////////////////////////////////////////////////////

    ServiceContext      SContext;
    Map<String, Object> FilterObject = new HashMap<>();

    public WorkHandler(ServiceContext sc) {
        SContext = sc;
    }

    void doFilter(Deal deal, int interceptor) {
        if ( Bootstrap.FilterList.isEmpty() )
            return;
        Object[] filters = Bootstrap.FilterList.toArray();
        if ( interceptor > 0 ) {
            for ( int i = 0; i < filters.length / 2; i ++ ) {
                Object f = filters[i];
                filters[i] = filters[filters.length - 1 - i];
                filters[filters.length - 1 - i] = f;
            }
        }
        for ( int i = 0; i < filters.length; i ++ ) {
            Filter filter = (Filter)filters[i];
            if ( filter.Status <= 0 )
                continue;
            Method method = interceptor < 0 ? filter.EntryBefore : filter.EntryAfter;
            if ( method == null )
                continue;
            synchronized (deal) {
                deal.InFilter = filter.JClass.getName();
                deal.Interceptor = interceptor;
                deal.Timeout = interceptor < 0 ? filter.TimeoutBefore : filter.TimeoutAfter;
                deal.DealTime = System.nanoTime();
            }
            try {
                Object o = FilterObject.get(deal.InFilter);
                if ( o == null ) {
                    if ( interceptor > 0 )
                        continue;
                    o = filter.JClass.newInstance();
                    FilterObject.put(deal.InFilter, o);
                }
                SContext.Filter = deal.InFilter;
                method.invoke(o, SContext);
            } catch (Exception e) {
                SContext.setResultException(e);
                Bootstrap.log(LogUtil.ERROR, deal.Service + "#" + deal.Entry + "()#" + deal.InFilter + (interceptor < 0 ? "@Before" : "@After"), e);
                break;
            }
            if ( interceptor < 0 && SContext.hasResult() )
                break;
        }
        SContext.Filter = null;
    }

    public void run() {
        Deal deal = new Deal();
        Bootstrap.RequestDeal.incrementAndGet();
        deal.Service = SContext.Service;
        deal.Entry = SContext.Entry;
        deal.Client = SContext.Remote.getHostAddress();

        boolean isForceLog = SContext.isForceLog();
        if ( isForceLog )
            Bootstrap.log(LogUtil.ACCESS, "enter", new LogBody.Enter(SContext.ReqID, SContext.Service, SContext.Entry, SContext.Flag, deal.Client));

        if ( Bootstrap.TimeoutFuse > 0 ) {
            int timeout_count = 0;
            for ( Deal dealing : Dealing.values() ) {
                if ( !deal.Service.equals(dealing.Service) )
                    continue;
                if ( !deal.Entry.equals(dealing.Entry) )
                    continue;
                synchronized (dealing) {
                    if (dealing.Timeout == 0)
                        continue;
                    long nano = TimeUnit.NANOSECONDS.convert(dealing.Timeout, TimeUnit.SECONDS);
                    if ( System.nanoTime() - dealing.DealTime > nano ) {
                        timeout_count ++;
                        if ( timeout_count >= Bootstrap.TimeoutFuse ) {
                            SContext.setResult(ErrorCode.BREAK, deal.Service + "#" + deal.Entry + "() in timeout");
                            break;
                        }
                    }
                }
            }
        }
        Dealing.put(SContext.ReqID, deal);

        if ( !SContext.Result ) {
            Service srv = Bootstrap.ServiceMap.get(SContext.Service);
            if (srv == null) {
                // 未找到服务
                if (Bootstrap.Forward == 0)
                    SContext.setResult(ErrorCode.NOSERVICE, "service \"" + SContext.Service + "\" not found");
                else {
                    Bootstrap.RequestForward.incrementAndGet();
                    try {
                        SContext.Param[0] = SContext.Entry;
                        SContext.forward();
                        SContext.Forwarded = true;
                    } catch (Exception e) {
                        SContext.setResult(ErrorCode.FORWARD, e.toString());
                    }
                }
            } else if (srv.Status <= 0) {
                // 服务状态异常
                SContext.setResult(ErrorCode.STOP, SContext.Service + (srv.Status == 0 ? " not start" : (srv.Status == -2 ? " waiting start" : " paused")));
            } else {
                doFilter(deal, -1);
                synchronized (deal) {
                    deal.InFilter = null;
                    deal.Interceptor = 0;
                    deal.Timeout = 0;
                }
                if ( !SContext.Result ) {
                    srv.RequestDeal.incrementAndGet();
                    Service.Entry entry = srv.EntryMap.get(SContext.Entry);
                    if (entry == null) {
                        // 未找到方法
                        SContext.setResult(ErrorCode.NOENTRY, SContext.Service + "#" + SContext.Entry + "() not found");
                    } else if ( SContext.prepareParams(entry) ) {
                        // 检查访问权限
                        if (!ServiceAcl.check(SContext.Service, entry, SContext.Remote))
                            SContext.setResult(ErrorCode.REJECT, SContext.Service + "#" + SContext.Entry + "() access denied"); // 拒绝访问
                        else {
                            entry.RequestDeal.incrementAndGet();
                            Object o = null;
                            long t = 0;
                            boolean interceptor = false;
                            try {
                                o = srv.JClass.newInstance();
                                if (srv.EntryBefore != null) {
                                    synchronized (deal) {
                                        deal.Interceptor = -1;
                                        deal.Timeout = srv.TimeoutBefore;
                                        deal.DealTime = System.nanoTime();
                                    }
                                    interceptor = true;
                                    srv.EntryBefore.invoke(o, SContext);
                                    interceptor = false;
                                }
                                if (!SContext.Result) {
                                    synchronized (deal) {
                                        deal.Interceptor = 0;
                                        deal.Timeout = entry.JAnnotation.timeout();
                                        if ( deal.Timeout < 1 )
                                            deal.Timeout = 1;
                                        deal.DealTime = System.nanoTime();
                                    }
                                    t = System.currentTimeMillis();
                                    Object res = entry.JMethod.invoke(o, SContext.Param);
                                    t = System.currentTimeMillis() - t;
                                    SContext.setResultData(res);
                                }
                            } catch (Exception e) {
                                entry.RequestError.incrementAndGet();
                                srv.RequestError.incrementAndGet();
                                SContext.setResultException(e);
                                Bootstrap.log(LogUtil.ERROR, deal.Service + "#" + deal.Entry + "()" + (interceptor ? "@Before" : "@invoke"), e);
                            } finally {
                                if (o != null && srv.EntryAfter != null) {
                                    synchronized (deal) {
                                        deal.Interceptor = 1;
                                        deal.Timeout = srv.TimeoutAfter;
                                        deal.DealTime = System.nanoTime();
                                    }
                                    try {
                                        srv.EntryAfter.invoke(o, SContext);
                                    } catch (Exception e) {
                                        if ( !SContext.hasResult() || SContext.getResultCode() == ErrorCode.OK ) {
                                            entry.RequestError.incrementAndGet();
                                            srv.RequestError.incrementAndGet();
                                            SContext.setResultException(e);
                                        }
                                        Bootstrap.log(LogUtil.ERROR, deal.Service + "#" + deal.Entry + "()@After", e);
                                    }
                                    synchronized (deal) {
                                        deal.Interceptor = 0;
                                        deal.Timeout = 0;
                                    }
                                }
                                if ( t > 0 )
                                    if (Util.setLarger(entry.RequestTime, t))
                                        entry.RequestID = SContext.ReqID;
                            }
                            entry.RequestOver.incrementAndGet();
                        }
                    }
                    srv.RequestOver.incrementAndGet();
                }
                doFilter(deal, 1);
                synchronized (deal) {
                    deal.InFilter = null;
                    deal.Interceptor = 0;
                    deal.Timeout = 0;
                }
                FilterObject.clear();
            }
        }
        if ( !SContext.Forwarded && SContext.Result )
            try {
                SContext.response();
            } catch (Exception e) {
                Bootstrap.log(LogUtil.ERROR, deal.Service + "#" + deal.Entry + "()@response", e);
            }
        if ( isForceLog )
            Bootstrap.log(LogUtil.ACCESS, "leave", new LogBody.Result(SContext.ReqID, SContext.Service, SContext.Entry,
                    SContext.Result ? SContext.ResultCode : null, SContext.Result ? SContext.ResultData : null,
                    TimeUnit.MILLISECONDS.convert(System.nanoTime() - deal.StartTime, TimeUnit.NANOSECONDS)));
        Bootstrap.RequestOver.incrementAndGet();
        Dealing.remove(SContext.ReqID);
    }
}
