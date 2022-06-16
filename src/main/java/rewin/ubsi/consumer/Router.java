package rewin.ubsi.consumer;

import redis.clients.jedis.Jedis;
import rewin.ubsi.common.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * UBSI客户端路由选择
 */
class Router {

    volatile static Map<String, Register.Container> Containers = null;   // 容器注册数据
    static ConcurrentMap<String, Long> Disabled = new ConcurrentHashMap<>();    // 节点连接失败的时间戳

    static ConcurrentLinkedQueue<String> Heartbeats = new ConcurrentLinkedQueue<>();    // 收到的容器广播消息：
                                                                                        // ip#port[|waiting] : 心跳
                                                                                        // ip#port|+ : 变更
                                                                                        // ip#port|- : 关闭

    static Set<String>  ActiveContainer = new HashSet<>();              // 活动的容器
    static long         ClearTimestamp = System.currentTimeMillis();    // 清理过期容器的时间戳

    // 加载全部的服务注册表，仅在Context.initJedis()中调用
    static synchronized void loadRegister() throws Exception {
        Map<String, Register.Container> containers = Context.getRegister(Context.REG_CONTAINER, Register.Container.class);
        // redis中的数据可能是容器在30秒之前更新的，需要将之前100秒之后更新的容器设置为有效
        long t = System.currentTimeMillis();
        if ( containers != null )
            for ( Register.Container ctn : containers.values() )
                if ( t - ctn.Timestamp < Context.BEATHEART_RECV * 10 * 1000 )
                    ctn.Timestamp = t - Context.BEATHEART_SEND * 1000;

        Router.Containers = containers;
        ActiveContainer.clear();
        ClearTimestamp = t;
    }

    /* 读取Redis中的单个容器注册数据 */
    static Register.Container loadOneRegister(String key) {
        try (Jedis jedis = JedisUtil.getJedis()) {
            byte[] reg = jedis.hget(Context.REG_CONTAINER, key.getBytes());
            if ( reg != null ) {
                Object obj = Codec.decodeBytes(reg);
                return Codec.toType(obj, Register.Container.class);
            }
        } catch (Exception e) {
            Context.log(LogUtil.ERROR, "load-register-" + key, e);
        }
        return null;
    }
    /* 处理容器心跳/更新的通知消息，在Timer中轮询调用(30ms) */
    static synchronized void dealHeartbeat() {
        Map<String, Register.Container> containers = Containers;
        Map<String, Register.Container> newest = new HashMap<>();
        Map<String, Integer> new_wait = new HashMap<>();    // 2021/11/15
        while ( true ) {
            String msg = Heartbeats.poll();
            if ( msg == null )
                break;
            try {
                int split = msg.indexOf('|');
                int waiting = 0;
                String ctname;          // 容器名
                if ( split > 0 ) {
                    ctname = msg.substring(0, split);
                    msg = msg.substring(split + 1);
                    if ( "+".equals(msg) )
                        waiting = -1;   // 容器更新
                    else if ( "-".equals(msg) )
                        waiting = -2;   // 容器关闭
                    else {
                        // 容器心跳
                        try {
                            waiting = Integer.parseInt(msg);
                        } catch (Exception e) {
                            continue;       // heartbeat格式非法，丢弃
                        }
                        if (waiting < 0 )
                            continue;       // heartbeat格式非法，丢弃
                    }
                } else if ( split < 0 )
                    ctname = msg;
                else
                    continue;               // heartbeat格式非法，丢弃

                ActiveContainer.add(ctname);
                if ( waiting >= 0 || waiting == -2 ) {
                    // 心跳 或 关闭
                    Register.Container ctn = containers == null ? null : containers.get(ctname);
                    if (ctn != null) {
                        if (waiting >= 0) {
                            ctn.Timestamp = System.currentTimeMillis();
                            ctn.Waiting = waiting;
                        } else
                            ctn.Timestamp = 0;      // 设置"已关闭"
                        continue;
                    }
                    if (waiting < 0)
                        continue;       // 容器关闭且不在Containers中
                }
                // 发现新的容器 或 容器更新
                new_wait.put(ctname, waiting);  // 2021/11/15 先记录下来，消息都处理完成后再loadOneRegister()
            } catch (Exception e) {
                Context.log(LogUtil.ERROR, "heartbeat:" + msg, e);
            }
        }

        if ( !new_wait.isEmpty() ) {
            if ( containers != null )
                for ( Map.Entry<String, Register.Container> entry : containers.entrySet() )
                    newest.put(entry.getKey(), entry.getValue());   // 复制原有的Register
            // 加载新的Register
            for ( Map.Entry<String, Integer> entry : new_wait.entrySet() ) {
                String ctname = entry.getKey();
                Register.Container ctnew = loadOneRegister(ctname);
                if ( ctnew == null )
                    continue;
                ctnew.Timestamp = System.currentTimeMillis();   // 修正心跳时间为本机时间
                int wait = entry.getValue();
                if ( wait >= 0 )
                    ctnew.Waiting = wait;
                newest.put(ctname, ctnew);
            }
            Containers = newest;
        }

        // 清理过期的容器（超过10秒未收到心跳）
        if ( System.currentTimeMillis() - ClearTimestamp > Context.BEATHEART_RECV * 1000 && Containers != null ) {
            containers = Containers;
            newest = new HashMap<>();
            List<byte[]> bads = new ArrayList<>();
            for ( Map.Entry<String, Register.Container> entry : containers.entrySet() ) {
                String ctname = entry.getKey();
                if ( ActiveContainer.contains(ctname) )
                    newest.put(ctname, entry.getValue());
                else
                    bads.add(ctname.getBytes());
            }
            if ( !bads.isEmpty() ) {
                try (Jedis jedis = JedisUtil.getJedis()) {
                    jedis.hdel(Context.REG_CONTAINER, bads.toArray(new byte[bads.size()][]));
                } catch (Exception e) { }
                Containers = newest;
            }
            // 重新开始记录活动心跳
            ActiveContainer.clear();
            ClearTimestamp = System.currentTimeMillis();
        }
    }

    /* 记录连接失败节点的时间戳 */
    static void disableRegister(String addr, boolean disable) {
        if ( disable )
            Disabled.put(addr, System.currentTimeMillis());
        else
            Disabled.remove(addr);
    }

    static Register.Router[] LocalTable; // 本地路由配置，按照Service/Entry长度降序配列

    /* 根据路由配置获得[host, port]，如果仅返回1个元素，则表示Mock的结果 */
    static Object[] getServer(String service, String entry, int vmin, int vmax, int vrel) throws Exception {
        Map<String, Register.Container> containers = Containers;    // 排除数据动态变化的影响
        Register.Router[] tables = LocalTable;   // 排除数据动态变化的影响
        // 检查本地路由
        if ( tables != null ) {
            for ( int i = 0; i < tables.length; i ++ ) {
                if ( !Util.matchString(service, tables[i].Service) )
                    continue;
                if ( !Util.matchString(entry, tables[i].Entry) )
                    continue;
                if ( vmin > 0 && tables[i].VerMin > 0 && vmin < tables[i].VerMin )
                    continue;
                if ( vmax > 0 && tables[i].VerMax > 0 && vmax > tables[i].VerMax )
                    continue;
                if ( vrel >= 0 && tables[i].VerRelease >= 0 ) {
                    if ( tables[i].VerRelease > 0 && vrel == 0 )
                        continue;
                    if ( tables[i].VerRelease == 0 && vrel > 0 )
                        continue;
                }
                if ( tables[i].Mock ) {
                    try {
                        return new Object[]{Context.getMockData(service, entry)};
                    } catch (Exception e) {
                        throw new Context.ResultException(ErrorCode.MOCK, "mock data of " + service + "#" + entry + "() error: " + e.getMessage());
                    }
                }
                Register.Node[] lnodes = tables[i].Nodes;
                if ( lnodes == null )
                    break;      // 不指定本地路由
                if ( lnodes.length == 0 )
                    throw new Context.ResultException(ErrorCode.ROUTER, "no routing path by Local-Setting for " + service);
                return selectNode(lnodes, true);
            }
        }
        // 检查动态路由
        if ( containers != null ) {
            List<Register.Node> nodes = new ArrayList<>();
            long t = System.currentTimeMillis();
            Map reg_ctns = new HashMap();
            for ( Map.Entry<String, Register.Container> ctn : containers.entrySet() ) {
                String ctn_name = ctn.getKey();
                Register.Container container = ctn.getValue();
                reg_ctns.put(ctn_name, "not found or inactived");
                Register.Service ms = container.Services.get(service);
                if ( ms == null || ms.Status != 1 )
                    continue;       // 未找到服务或服务不正常
                reg_ctns.put(ctn_name, "version mismatch");
                if ( vmin > 0 && ms.Version < vmin )
                    continue;
                if ( vmax > 0 && ms.Version > vmax )
                    continue;
                if ( vrel >= 0 ) {
                    if ( ms.Release && vrel == 0 )
                        continue;
                    if ( !ms.Release && vrel > 0 )
                        continue;
                }
                reg_ctns.put(ctn_name, "invalid-timestamp: " + (t - container.Timestamp));
                if ( container.isInvalid(t) )
                    continue;       // 容器不可用
                reg_ctns.put(ctn_name, "disabled");
                Long disabled = Disabled.get(ctn_name);
                if ( disabled != null && disabled > container.Timestamp )
                    continue;       // 连接失败后没有重新注册
                Register.Node node = new Register.Node();
                try {
                    String[] hp = ctn_name.split("#");
                    node.Host = hp[0];
                    node.Port = Integer.parseInt(hp[1]);
                    reg_ctns.put(ctn_name, "overload");
                    if ( container.Waiting >= container.Overload )
                        continue;       // 已经满负载
                    int wait = container.Waiting == 0 ? 1 : container.Waiting;
                    node.Weight = (double)container.Overload / wait;    // 根据负载情况计算权重
                    if ( t - container.Timestamp > Context.BEATHEART_RECV * 1000 / 2 )
                        node.Weight /= 2;   // 容器不太健康，降低权重
                } catch (Exception e) {
                    Context.log(LogUtil.ERROR, "router-" + service, e.toString());
                    continue;
                }
                nodes.add(node);
            }
            if ( nodes.isEmpty() ) {
                if ( !LogUtil.LOG_SERVICE.equals(service) )
                    Context.log(LogUtil.WARN, "router-" + service, reg_ctns);
                throw new Context.ResultException(ErrorCode.ROUTER, "no routing path by Register for " + service);
            }
            return selectNode(nodes.toArray(new Register.Node[nodes.size()]), false);
        }
        throw new Context.ResultException(ErrorCode.ROUTER, "no valid routing path for " + service);
    }

    // 根据权重选择容器
    static Object[] selectNode(Register.Node[] nodes, boolean checkDisable) {
        if ( nodes.length == 1 )
            return new Object[] { nodes[0].Host, nodes[0].Port };
        double sum = 0;
        double[] weight = new double[nodes.length];
        long t = System.currentTimeMillis();
        for ( int i = 0; i < nodes.length; i ++ ) {
            weight[i] = nodes[i].Weight;
            if ( checkDisable ) {
                Long dt = Disabled.get(nodes[i].Host + "#" + nodes[i].Port);
                if ( dt != null ) {
                    if ( t - dt < Context.TimeoutReconnect * 1000 )
                        weight[i] = 0;      // 节点曾经失败，未到超时重试时间
                    else
                        weight[i] /= 2;     // 节点曾经失败，到了超时重试时间
                }
            }
            sum += weight[i];     // 总权重
        }
        for ( int i = 1; i < weight.length; i ++ )
            weight[i] += weight[i-1];
        sum = Math.random() * sum;        // 随机数
        for ( int i = 0; i < weight.length; i ++ )
            if ( weight[i] >= sum )
                return new Object[] { nodes[i].Host, nodes[i].Port };   // 根据权重分布随机选择节点（权重高的概率更高）
        return new Object[] { nodes[0].Host, nodes[0].Port };
    }
}
