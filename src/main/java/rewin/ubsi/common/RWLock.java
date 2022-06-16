package rewin.ubsi.common;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 进程内全局读写锁
 */
public class RWLock implements AutoCloseable {

    private ReentrantReadWriteLock.ReadLock readLock = null;
    private ReentrantReadWriteLock.WriteLock writeLock = null;

    // try()模式自动关闭
    public void close() {
        if ( readLock != null ) {
            readLock.unlock();
            readLock = null;
        }
        if ( writeLock != null ) {
            writeLock.unlock();
            writeLock = null;
        }
    }

    ///////////////////////////////////////////////////

    static ConcurrentMap<String, ReentrantReadWriteLock> Locks = new ConcurrentHashMap<>();

    static ReentrantReadWriteLock getLock(String key) {
        ReentrantReadWriteLock lock = Locks.get(key);
        if ( lock != null )
            return lock;
        lock = new ReentrantReadWriteLock();
        ReentrantReadWriteLock cur = Locks.putIfAbsent(key, lock);
        return cur == null ? lock : cur;
    }

    /** 读锁 */
    public static RWLock lockRead(String key) {
        RWLock lock = new RWLock();
        lock.readLock = getLock(key).readLock();
        lock.readLock.lock();
        return lock;
    }
    /** 写锁 */
    public static RWLock lockWrite(String key) {
        RWLock lock = new RWLock();
        lock.writeLock = getLock(key).writeLock();
        lock.writeLock.lock();
        return lock;
    }
    /** 写锁 */
    public static RWLock lockWrite(String key, int timeout) throws Exception {
        RWLock lock = new RWLock();
        lock.writeLock = getLock(key).writeLock();
        if ( lock.writeLock.tryLock(timeout, TimeUnit.SECONDS) )
            return lock;
        lock.writeLock = null;
        throw new Exception("lock timeout");
    }

}
