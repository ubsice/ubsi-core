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
 * 错误代码
 */
public class ErrorCode {
    public final static int OK = 0;             // 处理成功
    public final static int OVERLOAD = 1;       // 过载
    public final static int SHUTDOWN = 2;       // 正在关闭
    public final static int NOSERVICE = 3;      // 服务未发现
    public final static int NOENTRY = 4;        // 接口未发现
    public final static int STOP = 5;           // 服务已停止
    public final static int REJECT = 6;         // 没有权限
    public final static int EXCEPTION = 7;      // 处理异常
    public final static int FORWARD = 8;        // 转发异常
    public final static int BREAK = 9;          // 接口超时，熔断
    public final static int TRANSACTION = 10;   // 事务处理失败
    public final static int PARAMS = 11;        // 参数错误
    public final static int ERROR = 100;        // 自定义错误

    public final static int REQUEST = -1;       // 请求参数异常
    public final static int CONNECT = -2;       // 连接异常
    public final static int CHANNEL = -3;       // Socket通讯异常
    public final static int TIMEOUT = -4;       // 请求超时
    public final static int ROUTER = -5;        // 路由失败
    public final static int MESSAGE = -6;       // 消息机制无效
    public final static int MOCK = -7;          // 仿真数据无效
    public final static int FILTER = -8;        // 请求过滤器拦截
    public final static int REPEAT = -9;        // 请求重复发送
}
