package rewin.ubsi.consumer;

/**
 * 定义日志数据的格式
 */
public class LogBody {

    /** Consumer的request（ACCESS日志） */
    public static class Request {
        public String   reqId;
        public String   seqId;      // 前置请求的ID
        public String   service;
        public String   entry;
        public byte     flag;

        public Request(String reqID, String seqID, String service, String entry, byte flag) {
            this.reqId = reqID;
            this.seqId = seqID;
            this.service = service;
            this.entry = entry;
            this.flag = flag;
        }
    }

    /** Container的enter（ACCESS日志） */
    public static class Enter {
        public String   reqId;
        public String   service;
        public String   entry;
        public byte     flag;
        public String   from;       // 访问者的IP

        public Enter(String reqID, String service, String entry, byte flag, String from) {
            this.reqId = reqID;
            this.service = service;
            this.entry = entry;
            this.flag = flag;
            this.from = from;
        }
    }

    /** Consumer的result/Container的leave（ACCESS日志） */
    public static class Result {
        public String   reqId;
        public String   service;
        public String   entry;
        public Integer  resCode;
        public String   errMsg;     // 如果成功则为null
        public long     time;       // 耗时，毫秒

        public Result(String reqID, String service, String entry, Integer resCode, Object resData, long time) {
            this.reqId = reqID;
            this.service = service;
            this.entry = entry;
            this.resCode = resCode;
            this.errMsg = resCode == ErrorCode.OK ? null : (resData == null ? null : resData.toString());
            this.time = time;
        }
    }
}
