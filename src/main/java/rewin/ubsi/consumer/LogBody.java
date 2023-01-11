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
