package rewin.ubsi.consumer;

import rewin.ubsi.common.LogUtil;

/**
 * UBSI日志记录器
 */
public class Logger {
    String AppTag;
    String AppID;

    /** 构造函数 */
    Logger(String appTag, String appID) {
        AppTag = appTag;
        AppID = appID;
    }

    /** 输出DEBUG日志 */
    public void debug(String tips, Object data) {
        LogUtil.log(LogUtil.DEBUG, AppTag, AppID, new Throwable(), 1, tips, data);
    }
    /** 输出INFO日志 */
    public void info(String tips, Object data) {
        LogUtil.log(LogUtil.INFO, AppTag, AppID, new Throwable(), 1, tips, data);
    }
    /** 输出WARN日志 */
    public void warn(String tips, Object data) {
        LogUtil.log(LogUtil.WARN, AppTag, AppID, new Throwable(), 1, tips, data);
    }
    /** 输出ERROR日志 */
    public void error(String tips, Object data) {
        LogUtil.log(LogUtil.ERROR, AppTag, AppID, new Throwable(), 1, tips, data);
    }
    /** 输出ACTION日志 */
    public void action(String tips, Object data) {
        LogUtil.log(LogUtil.ACTION, AppTag, AppID, new Throwable(), 1, tips, data);
    }
    /** 输出ACCESS日志 */
    public void access(String tips, Object data) {
        LogUtil.log(LogUtil.ACCESS, AppTag, AppID, new Throwable(), 1, tips, data);
    }
    /** 输出APP自定义级别日志 */
    public void log(int type, String tips, Object data) {
        LogUtil.log(type, AppTag, AppID, new Throwable(), 1, tips, data);
    }
    /** 输出APP自定义级别日志 */
    public void log(int type, int callStack, String tips, Object data) {
        LogUtil.log(type, AppTag, AppID, new Throwable(), 1 + callStack, tips, data);
    }
}
