package com.golaxy.zk;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.shared.SharedCountListener;
import org.apache.curator.framework.recipes.shared.SharedCountReader;
import org.apache.curator.framework.state.ConnectionState;

/**
 * Shared Couter Listener Impl
 * @link CuratorFrameworkTest.testSharedCount
 */
public class SharedCounterExample implements SharedCountListener {

    public void countHasChanged(SharedCountReader sharedCountReader, int i) throws Exception {
        System.out.println("计数器值改变：" + i);
    }

    public void stateChanged(CuratorFramework curatorFramework, ConnectionState connectionState) {
        System.out.println("连接状态: " + connectionState.toString());
    }
}
