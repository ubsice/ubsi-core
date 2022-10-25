package rewin.ubsi.common;

import redis.clients.jedis.*;
import redis.clients.jedis.util.Pool;

import java.util.*;

/**
 * Redis客户端工具
 */
public class JedisUtil {

    /** 缺省使用的数据库 */
    public final static int DATABASE = 3;

    final static int TIMEOUT = 10;                          // 连接/响应的超时时间
    final static String CHANNEL_EVENT = "_ubsi_event_";

    static Pool<Jedis> JedisPools = null;       // 连接池
    static Subscriber JedisSubscriber = null;   // 消息订阅者
    static Map<String, Set<Listener>> ChannelListener = new HashMap<>();
    static Map<String, Set<Listener>> PatternListener = new HashMap<>();
    static Map<String, Listener> EventListener = new HashMap<>();

    /**
     * 订阅消息监听器（注：使用此监听器，必须用JedisUtil.publish()接口来发送广播消息）
     *
     * 注：如果需要进行高耗时的操作，应启动另外的任务线程进行处理，以免阻塞异步I/O
     */
    public static abstract class Listener {
        /** 消息回调入口 */
        public abstract void onMessage(String channel, Object message) throws Exception;
        /** 事件回调入口 */
        public abstract void onEvent(String channel, Object event) throws Exception;

        /** 订阅消息 */
        public void subscribe(String... channels) {
            synchronized (ChannelListener) {
                List<String> add = addListener(ChannelListener, this, channels);
                if ( !add.isEmpty() )
                    synchronized (JedisSubscriber) {
                        JedisSubscriber.subscribe(add.toArray(new String[add.size()]));
                    }
            }
        }
        /** 订阅模式消息 */
        public void subscribePattern(String... patterns) {
            synchronized (PatternListener) {
                List<String> add = addListener(PatternListener, this, patterns);
                if ( !add.isEmpty() )
                    synchronized (JedisSubscriber) {
                        JedisSubscriber.psubscribe(add.toArray(new String[add.size()]));
                    }
            }
        }
        /** 订阅事件（同一个事件channel只能存在一个Listener） */
        public void subscribeEvent(String... channels) throws Exception {
            synchronized (EventListener) {
                for ( String channel : channels ) {
                    if ( channel == null || channel.isEmpty() )
                        continue;
                    EventListener.put(channel, this);
                }
            }
            try (Jedis jedis = JedisPools.getResource()) {
                for ( String channel : channels ) {
                    if (channel == null || channel.isEmpty())
                        continue;
                    while (true) {
                        byte[] data = jedis.rpop((CHANNEL_EVENT + ":" + channel).getBytes());
                        if (data == null)
                            break;
                        try { this.onEvent(channel, Codec.decodeBytes(data)); } catch (Exception e) {}
                    }
                }
            }
        }
        /** 取消消息订阅 */
        public void unsubscribe(String... channels) {
            synchronized (ChannelListener) {
                List<String> sub = subListener(ChannelListener, this, channels);
                if ( !sub.isEmpty() )
                    synchronized (JedisSubscriber) {
                        JedisSubscriber.unsubscribe(sub.toArray(new String[sub.size()]));
                    }
            }
        }
        /** 取消模式消息订阅 */
        public void unsubscribePattern(String... patterns) {
            synchronized (PatternListener) {
                List<String> sub = subListener(PatternListener, this, patterns);
                if ( !sub.isEmpty() )
                    synchronized (JedisSubscriber) {
                        JedisSubscriber.punsubscribe(sub.toArray(new String[sub.size()]));
                    }
            }
        }
        /** 取消事件订阅 */
        public void unsubscribeEvent(String... channels) {
            synchronized (EventListener) {
                for ( String channel : channels ) {
                    if ( channel == null || channel.isEmpty() )
                        continue;
                    EventListener.remove(channel, this);
                }
            }
        }
        /** 全部取消消息订阅 */
        public void unsubscribe() {
            synchronized (ChannelListener) {
                List<String> sub = subListener(ChannelListener, this);
                if ( !sub.isEmpty() )
                    synchronized (JedisSubscriber) {
                        JedisSubscriber.unsubscribe(sub.toArray(new String[sub.size()]));
                    }
            }
        }
        /** 全部取消模式消息订阅 */
        public void unsubscribePattern() {
            synchronized (PatternListener) {
                List<String> sub = subListener(PatternListener, this);
                if ( !sub.isEmpty() )
                    synchronized (JedisSubscriber) {
                        JedisSubscriber.punsubscribe(sub.toArray(new String[sub.size()]));
                    }
            }
        }
        /** 全部取消事件订阅 */
        public void unsubscribeEvent() {
            synchronized (EventListener) {
                List<String> un = new ArrayList<>();
                for ( Map.Entry<String, Listener> entry : EventListener.entrySet() ) {
                    if ( entry.getValue() == this )
                        un.add(entry.getKey());
                }
                for ( String channel : un )
                    EventListener.remove(channel);
            }
        }
    }

    /* 增加订阅者 */
    static List<String> addListener(Map<String, Set<Listener>> which, Listener listener, String... channels) {
        List<String> res = new ArrayList<>();
        for ( String channel : channels ) {
            if (channel == null || channel.isEmpty())
                continue;
            Set<Listener> set = which.get(channel);
            if (set == null) {
                set = new HashSet<>();
                which.put(channel, set);
                res.add(channel);
            }
            set.add(listener);
        }
        return res;
    }
    /* 减少订阅者 */
    static List<String> subListener(Map<String, Set<Listener>> which, Listener listener, String... channels) {
        List<String> res = new ArrayList<>();
        for ( String channel : channels ) {
            if (channel == null || channel.isEmpty())
                continue;
            Set<Listener> set = which.get(channel);
            if (set == null)
                continue;
            if (set.remove(listener)) {
                if (set.size() == 0) {
                    which.remove(channel);
                    res.add(channel);
                }
            }
        }
        return res;
    }
    /* 减少订阅者 */
    static List<String> subListener(Map<String, Set<Listener>> which, Listener listener) {
        List<String> res = new ArrayList<>();
        for ( Map.Entry<String, Set<Listener>> entry : which.entrySet() ) {
            Set<Listener> set = entry.getValue();
            if ( set.remove(listener) ) {
                if (set.size() == 0) {
                    String channel = entry.getKey();
                    res.add(channel);
                }
            }
        }
        for ( String channel : res )
            which.remove(channel);
        return res;
    }

    /* 消息订阅的回调通知 */
    static class Subscriber extends JedisPubSub {
        @Override
        public void onMessage(String channel, String message) {
            if ( CHANNEL_EVENT.equals(channel) ) {
                Listener listener = null;
                synchronized (EventListener) {
                    listener = EventListener.get(message);
                }
                if ( listener == null )
                    return;
                try (Jedis jedis = JedisPools.getResource()) {
                    while ( true ) {
                        byte[] data = jedis.rpop((CHANNEL_EVENT + ":" + message).getBytes());
                        if ( data == null )
                            break;
                        try { listener.onEvent(message, Codec.decodeBytes(data)); } catch (Exception e) {}
                    }
                } catch (Exception e) {}
                return;
            }
            Listener[] listeners = null;
            synchronized (ChannelListener) {
                Set<Listener> set = ChannelListener.get(channel);
                if ( set != null )
                    listeners = set.toArray(new Listener[set.size()]);
            }
            if ( listeners != null ) {
                try {
                    Object data = Codec.decode(message);
                    for (Listener listener : listeners)
                        try { listener.onMessage(channel, data); } catch (Exception e) {}
                } catch (Exception e) {
                }
            }
        }

        @Override
        public void onPMessage(String pattern, String channel, String message) {
            Listener[] listeners = null;
            synchronized (PatternListener) {
                Set<Listener> set = PatternListener.get(pattern);
                if ( set != null )
                    listeners = set.toArray(new Listener[set.size()]);
            }
            if ( listeners != null ) {
                try {
                    Object data = Codec.decode(message);
                    for (Listener listener : listeners)
                        try { listener.onMessage(channel, data); } catch (Exception e) {}
                } catch (Exception e) {
                }
            }
        }
    }

    // 消息订阅
    static void resubscribe() throws Exception {
        synchronized (ChannelListener) {
            Set<String> channels = ChannelListener.keySet();
            if ( !channels.isEmpty() )
                try { JedisSubscriber.subscribe(channels.toArray(new String[channels.size()])); } catch (Exception e) {}
        }
        synchronized (PatternListener) {
            Set<String> channels = PatternListener.keySet();
            if ( !channels.isEmpty() )
                try { JedisSubscriber.psubscribe(channels.toArray(new String[channels.size()])); } catch (Exception e) {}
        }
        synchronized (EventListener) {
            Set<String> channels = EventListener.keySet();
            if ( !channels.isEmpty() ) {
                try (Jedis jedis = JedisPools.getResource()) {
                    for (String channel : channels) {
                        if (channel == null || channel.isEmpty())
                            continue;
                        Listener listener = EventListener.get(channel);
                        try {
                            while (true) {
                                byte[] data = jedis.rpop((CHANNEL_EVENT + ":" + channel).getBytes());
                                if (data == null)
                                    break;
                                listener.onEvent(channel, Codec.decodeBytes(data));
                            }
                        } catch (Exception e) {}
                    }
                } catch (Exception e) {}
            }
        }
    }

    static Exception JedisInitException = null;

    // 初始化消息订阅
    static void initSubscribe() throws Exception {
        JedisSubscriber = new Subscriber();
        JedisInitException = null;
        new Thread(new Runnable() {
            public void run() {
                try (Jedis jedis = JedisPools.getResource()) {
                    jedis.subscribe(JedisSubscriber, CHANNEL_EVENT);
                } catch (Exception e) {
                    JedisInitException = e;
                }
                close();
            }
        }, "ubsi-jedis-subscribe").start();     // 启动Jedis订阅线程
        for ( int i = 0; i <= TIMEOUT; i ++ ) {
            if ( JedisInitException != null ) {
                new Thread(new Runnable() {
                    public void run() {
                        close();
                    }
                }, "ubsi-jedis-close").start();
                throw JedisInitException;
            }
            if ( JedisSubscriber.isSubscribed() ) {
                resubscribe();
                timeInit = System.currentTimeMillis();
                return;
            }
            try { Thread.sleep(1000); } catch (Exception e) {}
        }
        new Thread(new Runnable() {
            public void run() {
                close();
            }
        }, "ubsi-jedis-close").start();
        throw new Exception("redis subscriber timeout");
    }

    // 初始化连接池设置
    static JedisPoolConfig getConfig(int max_idle, int max_conn) {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMinIdle(1);
        config.setMaxIdle(max_idle);
        config.setMaxTotal(max_conn);
        config.setTestOnBorrow(true);
        config.setMaxWaitMillis(TIMEOUT * 1000);
        return config;
    }

    public static long timeInit = 0;
    public static long timeClose = 0;

    /** StandAlone模式初始化，pwd为null表示无密码 */
    public synchronized static void init(String host, int port, String pwd, int max_idle, int max_conn) throws Exception {
        if (JedisSubscriber != null)
            return;
        JedisPoolConfig config = getConfig(max_idle, max_conn);
        JedisPools = new JedisPool(config, host, port, TIMEOUT * 1000, TIMEOUT * 1000, pwd, DATABASE, "");
        initSubscribe();
    }

    /** Sentinel模式初始化，pwd为null表示无密码 */
    public synchronized static void init(String master, Set<String> nodes, String pwd, int max_idle, int max_conn) throws Exception {
        if (JedisSubscriber != null)
            return;
        JedisPoolConfig config = getConfig(max_idle, max_conn);
        JedisPools = new JedisSentinelPool(master, nodes, config, TIMEOUT * 1000, TIMEOUT * 1000, pwd, DATABASE);
        initSubscribe();
    }

    /** 关闭Jedis客户端 */
    public synchronized static void close() {
        if ( JedisSubscriber == null )
            return;
        try {
            synchronized (JedisSubscriber) {
                JedisSubscriber.unsubscribe();
                if ( !PatternListener.isEmpty() )
                    JedisSubscriber.punsubscribe();
                for (int i = 0; i <= TIMEOUT; i++) {
                    if (!JedisSubscriber.isSubscribed())
                        break;
                    try { Thread.sleep(1000); } catch (Exception e) {}
                }
            }
        } catch(Exception e) {}

        if ( JedisPools != null ) {
            JedisPools.close();
            JedisPools = null;
        }
        JedisSubscriber = null;
        timeClose = System.currentTimeMillis();
    }

    /** 获取一个Jedis实例（注：如果切换Database，需要在完成操作后切换回JedisUtil.DATABASE） */
    public static Jedis getJedis() {
        try {
            return JedisPools.getResource();
        } catch (Exception e) {
            close();
            throw e;
        }
    }

    /** 获得连接池状态 */
    public static int[] getPools() {
        int[] res = new int[2];
        res[0] = JedisPools.getNumActive();
        res[1] = JedisPools.getNumIdle();
        return res;
    }

    /** 获得订阅的频道 */
    public static Set<String> getSubChannels() {
        synchronized (ChannelListener) {
            return ChannelListener.keySet();
        }
    }
    /** 获得批量订阅的频道 */
    public static Set<String> getPSubChannels() {
        synchronized (PatternListener) {
            return PatternListener.keySet();
        }
    }
    /** 获得事件订阅的频道 */
    public static Set<String> getEventChannels() {
        synchronized (EventListener) {
            return EventListener.keySet();
        }
    }

    /** 是否初始化成功 */
    public synchronized static boolean isInited() {
        return JedisSubscriber != null;
    }

    /** 发送广播数据 */
    public static void publish(String channel, Object data) {
        if ( channel == null || channel.isEmpty() )
            return;
        try ( Jedis jedis = JedisPools.getResource() ) {
            jedis.publish(channel, Codec.encode(data));
        }
    }

    /** 发送事件 */
    public static void putEvent(String channel, Object data) {
        if ( channel == null || channel.isEmpty() )
            return;
        try ( Jedis jedis = JedisPools.getResource() ) {
            jedis.lpush((CHANNEL_EVENT + ":" + channel).getBytes(), Codec.encodeBytes(data));
            jedis.publish(CHANNEL_EVENT, channel);
        }
    }
    /** 查看未处理的事件 */
    public static List lookEvent(String channel) throws Exception {
        List<byte[]> list = null;
        try ( Jedis jedis = JedisPools.getResource() ) {
            list = jedis.lrange((CHANNEL_EVENT + ":" + channel).getBytes(), 0, -1);
        }
        List res = new ArrayList();
        if ( list != null )
            for ( byte[] data : list )
                res.add(Codec.decodeBytes(data));
        return res;
    }
    /** 清除事件 */
    public static void clearEvent(String channel) throws Exception {
        try ( Jedis jedis = JedisPools.getResource() ) {
            jedis.del((CHANNEL_EVENT + ":" + channel).getBytes());
        }
    }
    /** 获得所有Event Channel中的Event数量 */
    public static Map<String, Integer> getEventCounts() {
        Map<String, Integer> res = new HashMap<>();
        try ( Jedis jedis = JedisPools.getResource() ) {
            Set<byte[]> set = jedis.keys((CHANNEL_EVENT + ":*").getBytes());
            if ( set == null || set.isEmpty() )
                return res;
            List<byte[]> key = new ArrayList<>();
            Pipeline pipeline = jedis.pipelined();
            for ( byte[] k : set ) {
                pipeline.llen(k);
                key.add(k);
            }
            List<Object> value = pipeline.syncAndReturnAll();
            for ( int i = 0; i < key.size() && i < value.size(); i ++ )
                res.put(new String(key.get(i)), ((Long)value.get(i)).intValue());
        }
        return res;
    }
}
