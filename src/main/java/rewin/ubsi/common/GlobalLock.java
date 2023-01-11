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

package rewin.ubsi.common;

import redis.clients.jedis.Jedis;

/**
 * 分布式全局锁
 */
public class GlobalLock implements AutoCloseable {

    RWLock localLock = null;    // 本地读写锁
    String lockName = null;     // 锁名字，非null表示redis锁

    // try()模式自动关闭
    public void close() throws Exception {
        if ( localLock != null ) {
            localLock.close();
            localLock = null;
        } else if ( lockName != null ) {
            try ( Jedis jedis = JedisUtil.getJedis() ) {
                jedis.del(lockName);
                lockName = null;
            }
        }
    }

    //////////////////////////////////////////

    static final int LOCK_SECONDS = 60;     // redis缺省的锁定时间，超过会自动释放

    /** 获得分布式全局锁 */
    public static GlobalLock lock(String name, int lock_seconds) throws Exception {
        if ( name == null )
            throw new Exception("invalid name");
        GlobalLock lock = new GlobalLock();
        if ( !JedisUtil.isInited() ) {
            lock.localLock = RWLock.lockWrite(name, lock_seconds);
            return lock;
        }
        int try_senconds = 0;
        if ( lock_seconds <= 0 )
            lock_seconds = LOCK_SECONDS;
        while ( true ) {
            try ( Jedis jedis = JedisUtil.getJedis() ) {
                if ( jedis.setnx(name, "") > 0 ) {
                    lock.lockName = name;
                    jedis.expire(name, (long)lock_seconds);     // 设置延时
                    break;
                }
            }
            try_senconds ++;
            if ( try_senconds >= lock_seconds )
                throw new Exception("lock timeout");
            try { Thread.sleep(1000); } catch (Exception e) {}      // 等待redis释放锁
        }
        return lock;
    }

}
