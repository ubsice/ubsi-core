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
    static ConcurrentMap<String, Long> Disabled = new ConcurrentHashMap<>();    // 失败节点

    static ConcurrentLinkedQueue<String> Heartbeats = new ConcurrentLinkedQueue<>();    // 容器消息

    static Set<String>  ActiveContainer = new HashSet<>();              // 活动的容器
    static long         ClearTimestamp = System.currentTimeMillis();    // 清理时间戳

    // 加载全部的服务注册表
    static synchronized void loadRegister() throws Exception {
        Map<String, Register.Container> containers = Context.getRegister(Context.REG_CONTAINER, Register.Container.class);
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
    /* 处理容器心跳 */
    static synchronized void dealHeartbeat() {
        Map<String, Register.Container> containers = Containers;
        Map<String, Register.Container> newest = new HashMap<>();
        Map<String, Integer> new_wait = new HashMap<>();
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
                        waiting = -1;
                    else if ( "-".equals(msg) )
                        waiting = -2;
                    else {
                        try {
                            waiting = Integer.parseInt(msg);
                        } catch (Exception e) {
                            continue;
                        }
                        if (waiting < 0 )
                            continue;
                    }
                } else if ( split < 0 )
                    ctname = msg;
                else
                    continue;

                ActiveContainer.add(ctname);
                if ( waiting >= 0 || waiting == -2 ) {
                    Register.Container ctn = containers == null ? null : containers.get(ctname);
                    if (ctn != null) {
                        if (waiting >= 0) {
                            ctn.Timestamp = System.currentTimeMillis();
                            ctn.Waiting = waiting;
                        } else
                            ctn.Timestamp = 0;
                        continue;
                    }
                    if (waiting < 0)
                        continue;
                }
                new_wait.put(ctname, waiting);
            } catch (Exception e) {
                Context.log(LogUtil.ERROR, "heartbeat:" + msg, e);
            }
        }

        if ( !new_wait.isEmpty() ) {
            if ( containers != null )
                for ( Map.Entry<String, Register.Container> entry : containers.entrySet() )
                    newest.put(entry.getKey(), entry.getValue());
            for ( Map.Entry<String, Integer> entry : new_wait.entrySet() ) {
                String ctname = entry.getKey();
                Register.Container ctnew = loadOneRegister(ctname);
                if ( ctnew == null )
                    continue;
                ctnew.Timestamp = System.currentTimeMillis();
                int wait = entry.getValue();
                if ( wait >= 0 )
                    ctnew.Waiting = wait;
                newest.put(ctname, ctnew);
            }
            Containers = newest;
        }

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
            ActiveContainer.clear();
            ClearTimestamp = System.currentTimeMillis();
        }
    }

    /* 记录失败节点 */
    static void disableRegister(String addr, boolean disable) {
        if ( disable )
            Disabled.put(addr, System.currentTimeMillis());
        else
            Disabled.remove(addr);
    }

    static Register.Router[] LocalTable; // 本地路由配置

    /* 路由算法 */
    static Object[] getServer(String service, String entry, int vmin, int vmax, int vrel) throws Exception {
        Map<String, Register.Container> containers = Containers;
        Register.Router[] tables = LocalTable;
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

        if ( containers != null ) {
            List<Register.Node> nodes = new ArrayList<>();
            long t = System.currentTimeMillis();
            Map reg_ctns = new HashMap();
            for ( Map.Entry<String, Register.Container> ctn : containers.entrySet() ) {
                String ctn_name = ctn.getKey();
                Register.Container container = ctn.getValue();
                Register.Service ms = container.Services.get(service);
                if ( ms == null ) {
                    reg_ctns.put(ctn_name, "service not found");
                    continue;
                }
                if ( ms.Status != 1 ) {
                    reg_ctns.put(ctn_name, "invalid status: " + ms.Status);
                    continue;
                }
                if ( vmin > 0 && ms.Version < vmin ) {
                    reg_ctns.put(ctn_name, "min-version not match: " + Util.getVersion(ms.Version) + (ms.Release ? "-R" : "-B"));
                    continue;
                }
                if ( vmax > 0 && ms.Version > vmax ) {
                    reg_ctns.put(ctn_name, "max-version not match: " + Util.getVersion(ms.Version) + (ms.Release ? "-R" : "-B"));
                    continue;
                }
                if ( vrel >= 0 ) {
                    if ( ms.Release && vrel == 0 ) {
                        reg_ctns.put(ctn_name, "rel-version not match: " + Util.getVersion(ms.Version) + (ms.Release ? "-R" : "-B"));
                        continue;
                    }
                    if ( !ms.Release && vrel > 0 ) {
                        reg_ctns.put(ctn_name, "rel-version not match: " + Util.getVersion(ms.Version) + (ms.Release ? "-R" : "-B"));
                        continue;
                    }
                }
                if ( container.isInvalid(t) ) {
                    reg_ctns.put(ctn_name, "invalid timestamp: " + (t - container.Timestamp));
                    continue;
                }
                Long disabled = Disabled.get(ctn_name);
                if ( disabled != null && disabled > container.Timestamp ) {
                    reg_ctns.put(ctn_name, "disabled");
                    continue;
                }
                Register.Node node = new Register.Node();
                try {
                    String[] hp = ctn_name.split("#");
                    node.Host = hp[0];
                    node.Port = Integer.parseInt(hp[1]);
                    if ( container.Waiting >= container.Overload ) {
                        reg_ctns.put(ctn_name, "overload");
                        continue;
                    }
                    int wait = container.Waiting == 0 ? 1 : container.Waiting;
                    node.Weight = (double)container.Overload / wait;
                    if ( t - container.Timestamp > Context.BEATHEART_RECV * 1000 / 2 )
                        node.Weight /= 2;
                } catch (Exception e) {
                    Context.log(LogUtil.ERROR, "router-" + service, e.toString());
                    continue;
                }
                nodes.add(node);
            }
            if ( nodes.isEmpty() ) {
                if ( Context.LogNoRouting && !LogUtil.LOG_SERVICE.equals(service) )
                    Context.log(LogUtil.DEBUG, "router-" + service, reg_ctns);
                throw new Context.ResultException(ErrorCode.ROUTER, "no routing path by Register for " + service);
            }
            return selectNode(nodes.toArray(new Register.Node[nodes.size()]), false);
        }
        throw new Context.ResultException(ErrorCode.ROUTER, "no valid routing path for " + service);
    }

    // 选择容器
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
                        weight[i] = 0;
                    else
                        weight[i] /= 2;
                }
            }
            sum += weight[i];
        }
        for ( int i = 1; i < weight.length; i ++ )
            weight[i] += weight[i-1];
        sum = Math.random() * sum;
        for ( int i = 0; i < weight.length; i ++ )
            if ( weight[i] >= sum )
                return new Object[] { nodes[i].Host, nodes[i].Port };
        return new Object[] { nodes[0].Host, nodes[0].Port };
    }
}
