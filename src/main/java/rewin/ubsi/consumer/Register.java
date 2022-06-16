package rewin.ubsi.consumer;

import rewin.ubsi.common.JedisUtil;

import java.util.*;

/**
 * UBSI微服务的注册数据 及 本地路由配置
 */
public class Register {

    /** 过滤器信息 */
    public static class Filter {
        public String   ClassName;      // 类名字
        public int      Version;        // 版本
        public boolean  Release;        // 是否发行版
        public int      Status;         // 状态，0:未运行，1:运行，-1:暂停，-2:单例等待
        public boolean  Timeout;        // 是否有请求已经超时
    }
    /** 微服务信息 */
    public static class Service extends Filter {
        public long     Deal;           // 已处理的请求数
    }
    /** 服务容器 */
    public static class Container {
        public boolean  Gateway = false;// 是否转发
        public int      Overload;       // 等待处理的请求的最大数量
        public int      Waiting;        // 正在等待的请求数
        public long     Deal;           // 已处理的请求数
        public long     Timestamp;      // 时间戳，豪秒，0表示已关闭；注：全部修正为本机时间
        public Map<String, Service> Services = new HashMap<>();    // 加载的微服务
        public List<Filter> Filters;    // 加载的过滤器

        /* 是否有效容器 */
        public boolean isInvalid(long timestamp) {
            if ( Timestamp <= 0 )
                return true;            // 容器已关闭
            if ( JedisUtil.isInited() && (timestamp - Timestamp > Context.BEATHEART_RECV * 1000) )
                return true;            // 容器超时（redis还未失效）
            return false;
        }
    }

    /** 容器节点 */
    public static class Node {
        public String      Host;       // 主机名
        public int         Port;       // 端口号
        public double      Weight;     // 权重
    }
    /** 路由配置项 */
    public static class Router {
        public String      Service;         // 服务名字的前缀("*"表示通配符)，不能为null
        public String      Entry;           // 接口名字的前缀("*"表示通配符)，null表示不限
        public int         VerMin;          // 最低版本
        public int         VerMax;          // 最高版本，0表示不限
        public int         VerRelease = -1; // 是否正式版，1:是，0:不是，-1:不限
        public boolean     Mock = false;    // 是否模拟
        public Node[]      Nodes;           // 指定的Container节点，null表示不限
    }

    /** 请求统计 */
    public static class Statistics {
        public long     request;            // 计数器：总请求次数
        public long     result;             // 计数器：总返回次数
        public long     success;            // 计数器：总正常处理次数
        public long     max_time;           // 计时器：最长的处理时间（毫秒）
        public String   req_id;             // 最长处理时间的请求ID
    }

}
