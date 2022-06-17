package rewin.ubsi.consumer;

import io.netty.channel.Channel;
import rewin.ubsi.common.JedisUtil;
import rewin.ubsi.common.LogUtil;

import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.*;

/**
 * UBSI客户端的连接/请求管理
 */
class Connector {

    // Consumer的定时任务
    static class TimerDealer extends TimerTask {
        long TimestampCheckTimeout = 0;     // 检查请求超时的时间戳

        public void run() {
            // 检查请求timeout
            if ( System.currentTimeMillis() - TimestampCheckTimeout > Context.REQUEST_TIMEOUT ) {
                Set<Channel> set = DirectContext.keySet();
                for (Channel ch : set) {
                    Context context = DirectContext.get(ch);
                    if (!check(context))
                        continue;
                    synchronized (context) {
                        if (!setContextResult(context, ErrorCode.TIMEOUT, "request timeout"))
                            continue;
                        DirectContext.remove(ch);
                        ch.close();
                    }
                    context.resultCallback();
                }

                for (ConcurrentMap<String, Context> map : ChannelContext.values()) {
                    if (map == null)
                        continue;
                    for (Context context : map.values()) {
                        if (!check(context))
                            continue;
                        synchronized (context) {
                            if (!setContextResult(context, ErrorCode.TIMEOUT, "request timeout"))
                                continue;
                            map.remove(context.ReqID);
                        }
                        context.resultCallback();
                    }
                }

                for (Context context : MessageContext.values()) {
                    if (!check(context))
                        continue;
                    synchronized (context) {
                        if (!setContextResult(context, ErrorCode.TIMEOUT, "request timeout"))
                            continue;
                        MessageContext.remove(context.ReqID);
                    }
                    context.resultCallback();
                }

                TimestampCheckTimeout = System.currentTimeMillis();
            }

            if ( JedisUtil.isInited() )
                Router.dealHeartbeat();
            else
                Context.initJedis();
        }

        boolean check(Context context) {
            if ( context == null || context.ResultStatus || context.Notify == null || context.Timeout == 0 )
                return false;
            return System.currentTimeMillis() - context.RequestTime >= context.Timeout * 1000;
        }
    }

    static ConcurrentMap<String, FutureTask<Channel>> AddrChannel = new ConcurrentHashMap<String, FutureTask<Channel>>();
    static ConcurrentMap<Channel, String> ChannelAddr = new ConcurrentHashMap<Channel, String>();
    static ConcurrentMap<Channel, Context> DirectContext = new ConcurrentHashMap<Channel, Context>();
    static ConcurrentMap<Channel, ConcurrentMap<String, Context>> ChannelContext = new ConcurrentHashMap<Channel, ConcurrentMap<String, Context>>();
    static ConcurrentMap<String, Context> MessageContext = new ConcurrentHashMap<String, Context>();

    /** 获取连接 */
    static Channel get(final String host, final int port) throws Exception {
        final String addr = host + '#' + port;
        Future<Channel> fch = AddrChannel.get(addr);
        if ( fch != null )
            return fch.get();

        FutureTask<Channel> newTask = new FutureTask<Channel>(new Callable<Channel>() {
            public Channel call() throws Exception {
                try {
                    Channel ch = IOHandler.connect(host, port);
                    ChannelAddr.put(ch, addr);
                    ChannelContext.put(ch, new ConcurrentHashMap<String, Context>());
                    return ch;
                } catch (Exception e) {
                    AddrChannel.remove(addr);
                    throw e;
                }
            }
        });
        FutureTask<Channel> task = AddrChannel.putIfAbsent(addr, newTask);
        if ( task == null ) {
            task = newTask;
            task.run();
        }
        return task.get();
    }

    // 设置返回结果
    static boolean setContextResult(Context context, int code, Object data) {
        if ( context.ResultStatus )
            return false;
        context.setResult(code, data);
        context.logResult();
        return true;
    }

    /** 通过Socket得到请求结果 */
    static void setChannelResponse(Channel ch, String id, byte code, Object data) {
        Context context = DirectContext.get(ch);
        ConcurrentMap<String, Context> map = null;
        if ( context == null ) {
            map = ChannelContext.get(ch);
            if ( map == null )
                return;
            context = map.get(id);
            if ( context == null )
                return;
        }
        synchronized (context) {
            if ( !setContextResult(context, code, data) )
                return;
            if (context.Notify == null) {
                context.notifyAll();
                return;
            }
            if ( map == null ) {
                DirectContext.remove(ch);
                ch.close();
            } else
                map.remove(id);
        }
        context.resultCallback();
    }

    /** Socket异常 */
    static void setChannelException(Channel ch, String msg) {
        Context context = DirectContext.get(ch);
        if ( context != null ) {
            synchronized (context) {
                if ( !setContextResult(context, ErrorCode.CHANNEL, msg) )
                    return;
                if ( context.Notify == null ) {
                    context.notifyAll();
                    return;
                }
                DirectContext.remove(ch);
            }
            context.resultCallback();
            return;
        }

        String addr = ChannelAddr.remove(ch);
        if ( addr != null )
            AddrChannel.remove(addr);
        ConcurrentMap<String, Context> map = ChannelContext.remove(ch);
        if ( map == null )
            return;
        for ( Context ctx : map.values() ) {
            synchronized (ctx) {
                if ( !setContextResult(ctx, ErrorCode.CHANNEL, msg) )
                    continue;
                if ( ctx.Notify == null ) {
                    ctx.notifyAll();
                    continue;
                }
            }
            ctx.resultCallback();
        }
    }

    /** 保存通过Socket得到结果的请求 */
    static void putChannelContext(Channel ch, Context context, boolean put) throws Exception {
        ConcurrentMap<String, Context> map = ChannelContext.get(ch);
        if ( map == null ) {
            if ( put )
                throw new Context.ResultException(ErrorCode.CHANNEL, "socket channel invalid");
            setContextResult(context, ErrorCode.CHANNEL, "socket channel invalid");
            return;
        }
        if ( put )
            map.put(context.ReqID, context);
        else
            map.remove(context.ReqID);
    }

    /** 通过Message得到请求结果 */
    static void setMessageResponse(Object msg) throws Exception {
        Object[] res = (Object[]) msg;
        String id = (String) res[0];
        byte code = (Byte) res[1];
        Object data = res[2];

        Context context = MessageContext.get(id);
        if ( context == null )
            return;
        synchronized (context) {
            if ( !setContextResult(context, code, data) )
                return;
            MessageContext.remove(context.ReqID);
        }
        context.resultCallback();
    }

}
