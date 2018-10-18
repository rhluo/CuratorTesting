package com.golaxy.zk;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.framework.recipes.locks.InterProcessReadWriteLock;

import java.util.concurrent.TimeUnit;

/**
 * 用来管理资源的读写使用
 * @link CuratorFrameworkTest.testInterProcessReadWriteLock
 */
public class ExampleClientReadWriteLocks {
    private final InterProcessReadWriteLock lock;
    private final InterProcessMutex readLock;
    private final InterProcessMutex writeLock;
    private final FakeLimitedResource resource;
    private final String clientName;

    public ExampleClientReadWriteLocks(CuratorFramework client, String lockPath, FakeLimitedResource resource, String clientName)
    {
        this.resource = resource;
        this.clientName = clientName;
        lock = new InterProcessReadWriteLock(client, lockPath);
        readLock = lock.readLock();
        writeLock = lock.writeLock();
    }

    public void doWork(long time, TimeUnit unit) throws Exception {
        // 注意只能先得到写锁再得到读锁，不能反过来！！！
        if (!writeLock.acquire(time, unit))
        {
            throw new IllegalStateException(clientName + " 不能得到写锁");
        }
        System.out.println(clientName + " 已得到写锁");
        if (!readLock.acquire(time, unit))
        {
            throw new IllegalStateException(clientName + " 不能得到读锁");
        }
        System.out.println(clientName + " 已得到读锁");
        try {
            resource.use(); // 使用资源
            Thread.sleep(1000 * 1);
        } finally {
            System.out.println(clientName + " 释放读写锁");
            readLock.release();
            writeLock.release();
        }
    }
}