package rewin.ubsi.container;

import redis.clients.jedis.Jedis;
import rewin.ubsi.common.JedisUtil;
import rewin.ubsi.common.LogUtil;
import rewin.ubsi.consumer.Context;
import rewin.ubsi.consumer.Register;

import java.util.*;

/**
 * 处理单例服务
 */
class Singleton {
    
    final static String LOCK_KEY = "_ubsi_lock_ss_";

    /* 除了本容器之外，是否还有正在运行的服务实例 */
    static boolean hasActive(String name) {
        Map<String, Register.Container> reg = Context.getRegister();
        if ( reg == null )
            return false;
        return hasActive(name, reg, System.currentTimeMillis());
    }
    static boolean hasActive(String name, Map<String, Register.Container> reg, long t) {
        String self = Bootstrap.Host + "#" + Bootstrap.Port;
        for ( Map.Entry<String, Register.Container> entry : reg.entrySet() ) {
            String key = entry.getKey();
            if ( self.equals(key) )
                continue;
            Register.Container ctn = entry.getValue();
            if ( ctn.isInvalid(t) )
                continue;
            Register.Service srv = ctn.Services.get(name);
            if ( srv == null )
                continue;
            if ( srv.Status == 1 || srv.Status == -1 )
                return true;
        }
        return false;
    }

    /* 通过redis加/解锁 */
    static boolean lockStart(String name, boolean lock) throws Exception {
        try ( Jedis jedis = JedisUtil.getJedis() ) {
            String key = LOCK_KEY + name;
            String value = Bootstrap.Host + "#" + Bootstrap.Port;
            if ( lock ) {
                long res = jedis.setnx(key, value);
                if ( res == 0 )
                    return false;
                jedis.expire(key, 60L);     // 延时1分钟
            } else
                jedis.expire(key, 1L);      // 延时1秒钟
        }
        return true;
    }
}
