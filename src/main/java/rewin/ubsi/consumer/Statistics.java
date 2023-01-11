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

import rewin.ubsi.common.Util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * UBSI Consumer请求统计
 */
class Statistics {

    AtomicLong  request = new AtomicLong(0);    // 计数器：总发送次数
    AtomicLong  result = new AtomicLong(0);     // 计数器：总返回次数
    AtomicLong  success = new AtomicLong(0);    // 计数器：总正常处理次数
    AtomicLong  max_time = new AtomicLong(0);   // 计时器：最长的处理时间
    String      request_id = null;              // 最长处理时间的请求ID

    static ConcurrentMap<String, ConcurrentMap<String, Statistics>> Records = new ConcurrentHashMap<>();

    // 得到统计数据的实例
    static Statistics getStatistics(String service, String entry) {
        ConcurrentMap<String, Statistics> newmap = new ConcurrentHashMap<>();
        ConcurrentMap<String, Statistics> map = Records.putIfAbsent(service, newmap);
        if ( map == null )
            map = newmap;
        Statistics newrec = new Statistics();
        Statistics rec = map.putIfAbsent(entry, newrec);
        return rec == null ? newrec : rec;
    }

    /* 发送了一个请求 */
    static void send(String service, String entry) {
        Statistics rec = getStatistics(service, entry);
        rec.request.incrementAndGet();
    }

    /* 收到了请求结果 */
    static void recv(String service, String entry, int code, long time, String reqid) {
        Statistics rec = getStatistics(service, entry);
        rec.result.incrementAndGet();
        if ( code == ErrorCode.OK )
            rec.success.incrementAndGet();
        if ( time > 0 )
            if (Util.setLarger(rec.max_time, time))
                rec.request_id = reqid;
    }

}
