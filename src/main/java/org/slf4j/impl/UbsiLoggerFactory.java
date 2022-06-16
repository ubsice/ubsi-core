package org.slf4j.impl;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class UbsiLoggerFactory implements ILoggerFactory {
    ConcurrentMap<String, Logger> loggerMap = new ConcurrentHashMap();

    /**
    public SimpleLoggerFactory() {
        SimpleLogger.lazyInit();
    }
    */

    public Logger getLogger(String name) {
        Logger simpleLogger = (Logger)this.loggerMap.get(name);
        if (simpleLogger != null) {
            return simpleLogger;
        } else {
            Logger newInstance = new UbsiLogger(name);
            Logger oldInstance = (Logger)this.loggerMap.putIfAbsent(name, newInstance);
            return (Logger)(oldInstance == null ? newInstance : oldInstance);
        }
    }

    void reset() {
        this.loggerMap.clear();
    }
}
