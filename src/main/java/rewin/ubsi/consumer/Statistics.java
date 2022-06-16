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
    AtomicLong  max_time = new AtomicLong(0);   // 计时器：最长的处理时间（毫秒）
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
