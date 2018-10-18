package com.golaxy.zk;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 模拟限制资源
 * @link CuratorFrameworkTest.testInterProcessMutex
 */
public class FakeLimitedResource {
    //资源占用标志位
    private final AtomicBoolean inUse = new AtomicBoolean(false);

    // 模拟只能单线程操作的资源
    public void use() throws InterruptedException
    {
        if (!inUse.compareAndSet(false, true))
        {
            // 在正确使用锁的情况下，此异常不可能抛出
            throw new IllegalStateException("Needs to be used by one client at a time");
        }
        try {
            //System.out.println("beginning use the resource");
            Thread.sleep((long) (3 * Math.random()));
            //System.out.println("release the resource");
        } finally {
            inUse.set(false);
        }
    }
}