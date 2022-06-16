package org.slf4j.impl;

import org.slf4j.event.Level;
import org.slf4j.event.LoggingEvent;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MarkerIgnoringBase;
import org.slf4j.helpers.MessageFormatter;
import rewin.ubsi.common.LogUtil;
import rewin.ubsi.common.Util;

public class UbsiLogger extends MarkerIgnoringBase {
    private static final long serialVersionUID = -110717711209710911L;  // 序列化标识
    private static final int TRACE = Level.TRACE.toInt();
    private static final int DEBUG = Level.DEBUG.toInt();
    private static final int INFO = Level.INFO.toInt();
    private static final int WARN = Level.WARN.toInt();
    private static final int ERROR = Level.ERROR.toInt();
    private static final int NONE = ERROR + 1;
    private static final String TRACE_S = Level.TRACE.toString();
    private static final String DEBUG_S = Level.DEBUG.toString();
    private static final String INFO_S = Level.INFO.toString();
    private static final String WARN_S = Level.WARN.toString();
    private static final String ERROR_S = Level.ERROR.toString();
    private static final String NONE_S = "NONE";

    private static class LogApp {
        String  appTag;
        String  appID;
    }
    private static ThreadLocal<LogApp> localThread = new InheritableThreadLocal<>();
    private static int enableLevel = WARN;      // 默认的日志输出级别

    /** 设置本线程的日志APP */
    public static void setLogApp() {
        localThread.remove();
    }
    /** 设置本线程的日志APP */
    public static void setLogApp(String tag, String id) {
        LogApp logApp = localThread.get();
        if ( logApp == null )
            logApp = new LogApp();
        logApp.appTag = tag;
        logApp.appID = id;
        localThread.set(logApp);
    }
    /** 设置日志输出级别 */
    public static void setLogLevel(String level) {
        level = Util.checkEmpty(level);
        if ( level == null )
            return;
        if ( TRACE_S.equalsIgnoreCase(level) )
            enableLevel = TRACE;
        else if ( DEBUG_S.equalsIgnoreCase(level) )
            enableLevel = DEBUG;
        else if ( INFO_S.equalsIgnoreCase(level) )
            enableLevel = INFO;
        else if ( WARN_S.equalsIgnoreCase(level) )
            enableLevel = WARN;
        else if ( ERROR_S.equalsIgnoreCase(level) )
            enableLevel = ERROR;
        else if ( NONE_S.equalsIgnoreCase(level) )
            enableLevel = NONE;    // 关闭所有日志输出
    }
    /** 获取日志输出级别 */
    public static String getLogLevel() {
        if ( enableLevel <= TRACE )
            return TRACE_S;
        if ( enableLevel <= DEBUG )
            return DEBUG_S;
        if ( enableLevel <= INFO )
            return INFO_S;
        if ( enableLevel <= WARN )
            return WARN_S;
        if ( enableLevel <= ERROR )
            return ERROR_S;
        return NONE_S;
    }

    UbsiLogger(String name) {
        this.name = name;
    }

    private void log(int type, String message, Throwable t, int code_stack) {
        String appTag = "ubsi";
        String appID = "slf4j";
        LogApp logApp = localThread.get();
        if ( logApp != null ) {
            appTag = logApp.appTag;
            appID = logApp.appID;
        }
        LogUtil.log(type, appTag, appID, new Throwable(), 2 + code_stack, this.name, message);
        if ( t != null )
            LogUtil.log(type, appTag, appID, new Throwable(), 2 + code_stack, this.name, t);
    }
    private void formatAndLog(int level, String format, Object arg1, Object arg2) {
        FormattingTuple tp = MessageFormatter.format(format, arg1, arg2);
        this.log(level, tp.getMessage(), tp.getThrowable(), 1);
    }
    private void formatAndLog(int level, String format, Object... arguments) {
        FormattingTuple tp = MessageFormatter.arrayFormat(format, arguments);
        this.log(level, tp.getMessage(), tp.getThrowable(), 1);
    }

    public boolean isTraceEnabled() {
        return enableLevel <= TRACE;
    }
    public void trace(String msg) {
        if ( isTraceEnabled() )
            this.log(LogUtil.DEBUG, msg, (Throwable)null, 0);
    }
    public void trace(String format, Object param1) {
        if ( isTraceEnabled() )
            this.formatAndLog(LogUtil.DEBUG, format, param1, (Object)null);
    }
    public void trace(String format, Object param1, Object param2) {
        if ( isTraceEnabled() )
            this.formatAndLog(LogUtil.DEBUG, format, param1, param2);
    }
    public void trace(String format, Object... argArray) {
        if ( isTraceEnabled() )
            this.formatAndLog(LogUtil.DEBUG, format, argArray);
    }
    public void trace(String msg, Throwable t) {
        if ( isTraceEnabled() )
            this.log(LogUtil.DEBUG, msg, t, 0);
    }

    public boolean isDebugEnabled() {
        return enableLevel <= DEBUG;
    }
    public void debug(String msg) {
        if ( isDebugEnabled() )
            this.log(LogUtil.DEBUG, msg, (Throwable)null, 0);
    }
    public void debug(String format, Object param1) {
        if ( isDebugEnabled() )
            this.formatAndLog(LogUtil.DEBUG, format, param1, (Object)null);
    }
    public void debug(String format, Object param1, Object param2) {
        if ( isDebugEnabled() )
            this.formatAndLog(LogUtil.DEBUG, format, param1, param2);
    }
    public void debug(String format, Object... argArray) {
        if ( isDebugEnabled() )
            this.formatAndLog(LogUtil.DEBUG, format, argArray);
    }
    public void debug(String msg, Throwable t) {
        if ( isDebugEnabled() )
            this.log(LogUtil.DEBUG, msg, t, 0);
    }

    public boolean isInfoEnabled() {
        return enableLevel <= INFO;
    }
    public void info(String msg) {
        if ( isInfoEnabled() )
            this.log(LogUtil.INFO, msg, (Throwable)null, 0);
    }
    public void info(String format, Object arg) {
        if ( isInfoEnabled() )
            this.formatAndLog(LogUtil.INFO, format, arg, (Object)null);
    }
    public void info(String format, Object arg1, Object arg2) {
        if ( isInfoEnabled() )
            this.formatAndLog(LogUtil.INFO, format, arg1, arg2);
    }
    public void info(String format, Object... argArray) {
        if ( isInfoEnabled() )
            this.formatAndLog(LogUtil.INFO, format, argArray);
    }
    public void info(String msg, Throwable t) {
        if ( isInfoEnabled() )
            this.log(LogUtil.INFO, msg, t, 0);
    }

    public boolean isWarnEnabled() {
        return enableLevel <= WARN;
    }
    public void warn(String msg) {
        if ( isWarnEnabled() )
            this.log(LogUtil.WARN, msg, (Throwable)null, 0);
    }
    public void warn(String format, Object arg) {
        if ( isWarnEnabled() )
            this.formatAndLog(LogUtil.WARN, format, arg, (Object)null);
    }
    public void warn(String format, Object arg1, Object arg2) {
        if ( isWarnEnabled() )
            this.formatAndLog(LogUtil.WARN, format, arg1, arg2);
    }
    public void warn(String format, Object... argArray) {
        if ( isWarnEnabled() )
            this.formatAndLog(LogUtil.WARN, format, argArray);
    }
    public void warn(String msg, Throwable t) {
        if ( isWarnEnabled() )
            this.log(LogUtil.WARN, msg, t, 0);
    }

    public boolean isErrorEnabled() {
        return enableLevel <= ERROR;
    }
    public void error(String msg) {
        if ( isErrorEnabled() )
            this.log(LogUtil.ERROR, msg, (Throwable)null, 0);
    }
    public void error(String format, Object arg) {
        if ( isErrorEnabled() )
            this.formatAndLog(LogUtil.ERROR, format, arg, (Object)null);
    }
    public void error(String format, Object arg1, Object arg2) {
        if ( isErrorEnabled() )
            this.formatAndLog(LogUtil.ERROR, format, arg1, arg2);
    }
    public void error(String format, Object... argArray) {
        if ( isErrorEnabled() )
            this.formatAndLog(LogUtil.ERROR, format, argArray);
    }
    public void error(String msg, Throwable t) {
        if ( isErrorEnabled() )
            this.log(LogUtil.ERROR, msg, t, 0);
    }

    public void log(LoggingEvent event) {
        if ( event == null )
            return;
        int levelInt = event.getLevel().toInt();
        if ( levelInt < enableLevel || enableLevel >= NONE )
            return;
        if ( levelInt > WARN )
            levelInt = LogUtil.ERROR;
        else if ( levelInt > INFO )
            levelInt = LogUtil.WARN;
        else if ( levelInt > DEBUG )
            levelInt = LogUtil.INFO;
        else
            levelInt = LogUtil.DEBUG;
        FormattingTuple tp = MessageFormatter.arrayFormat(event.getMessage(), event.getArgumentArray(), event.getThrowable());
        this.log(levelInt, tp.getMessage(), event.getThrowable(), 0);
    }
}
