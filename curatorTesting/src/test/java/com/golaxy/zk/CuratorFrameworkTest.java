package com.golaxy.zk;

import com.google.common.collect.Lists;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.barriers.DistributedBarrier;
import org.apache.curator.framework.recipes.barriers.DistributedDoubleBarrier;
import org.apache.curator.framework.recipes.cache.*;
import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.curator.framework.recipes.locks.InterProcessMultiLock;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreMutex;
import org.apache.curator.framework.recipes.nodes.PersistentEphemeralNode;
import org.apache.curator.framework.recipes.queue.DistributedQueue;
import org.apache.curator.framework.recipes.queue.QueueBuilder;
import org.apache.curator.framework.recipes.queue.QueueConsumer;
import org.apache.curator.framework.recipes.queue.QueueSerializer;
import org.apache.curator.framework.recipes.shared.SharedCount;
import org.apache.curator.framework.recipes.shared.SharedCountReader;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.KillSession;
import org.apache.curator.test.TestingCluster;
import org.apache.curator.test.TestingServer;
import org.apache.curator.test.TestingZooKeeperServer;
import org.apache.curator.utils.CloseableUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

import org.junit.*;

/**
* use TestingServer to create client & cluster
* use server in IP_PORT to create client & cluster
*/
public class CuratorFrameworkTest {
    private static final String IP_PORT = "172.22.0.17:2181";
    private static final int CLIENT_QTY = 10;
    CuratorFramework client = null;
    TestingCluster cluster = null;
    TestingServer server = null;

    @Before
    public void before() throws Exception {
        //server = new TestingServer();
        //client = CuratorFrameworkFactory.newClient(server.getConnectString(), new ExponentialBackoffRetry(1000, 3));
        client = CuratorFrameworkFactory
                .newClient(IP_PORT, new ExponentialBackoffRetry(1000, 3));
        client.start();
        cluster = new TestingCluster(CLIENT_QTY);
        cluster.start();
    }

    @After
    public void after() throws Exception {
        //server.stop();
        cluster.stop();
        //用来封装try...close...catch
        CloseableUtils.closeQuietly(cluster);
        CloseableUtils.closeQuietly(client);
        CloseableUtils.closeQuietly(server);
    }

    /**
     * getClients use the TestingCluster
     * @param instanceQty
     * @return
     */
    List<CuratorFramework> getClientsForTest(int instanceQty) throws Exception{
        if(instanceQty > CLIENT_QTY) {
            throw new Exception("instanceQty is more than the testing cluster node number");
        }

        List<CuratorFramework> clients = new ArrayList<CuratorFramework>();
        for (TestingZooKeeperServer server : cluster.getServers()) {
            CuratorFramework client = CuratorFrameworkFactory.newClient(server.getInstanceSpec().getConnectString(),
                    new ExponentialBackoffRetry(1000, 3));
            clients.add(client);
            client.start();
        }
        return clients;
    }

    /**
     * getClients use the read server client
     * @param instanceQty
     * @return
     * @throws Exception
     */
    List<CuratorFramework> getClientsForRealServer(int instanceQty) throws Exception{
        if(instanceQty > CLIENT_QTY) {
            throw new Exception("instanceQty is more than the testing cluster node number");
        }

        List<CuratorFramework> clients = new ArrayList<CuratorFramework>();
        for (int i = 0;i < instanceQty;i++) {
            CuratorFramework client = CuratorFrameworkFactory.newClient(IP_PORT,
                    new ExponentialBackoffRetry(1000, 3));
            clients.add(client);
            client.start();
        }
        return clients;
    }

    @Test
    public void testCreateClient() throws Exception {
        client.getConnectionStateListenable().addListener((cl, newState)-> {
                System.out.println("连接状态:" + newState.name());
        });

        System.out.println(client.getConfig());

        List<String> paths = client.getChildren().forPath("/");
        System.out.println(paths);
        client.create().forPath("/test");

        paths = client.getChildren().forPath("/");
        System.out.println(paths);

        CloseableUtils.closeQuietly(client);
        System.out.println("OK!");
    }

    @Test
    public void testCreateCluster() throws Exception{
        for (TestingZooKeeperServer server : cluster.getServers())
        {
            System.out.println(server.getInstanceSpec());
        }

        System.out.println("OK！");
    }

    /**
     * LeaderLatch use to handle the leader election, the leader will work until LeaderLatch.close()
     * @throws Exception
     */
    @Test
    public void testLeaderLatch() throws Exception {
        String PATH = "/examples/leader";
        //forPath函数指定创建节点的path和保存的数据，path的指定遵循linux文件path格式，创建node时指定的path，
        //父path节点需要存在，否则创建节点失败，比如创建"/parent/child"节点，若不存在节点"parent"，那么创建节点会失败。
        //client.create().forPath("/examples");
        client.create().forPath(PATH);


        List<CuratorFramework> clients = getClientsForRealServer(CLIENT_QTY);
        List<LeaderLatch> examples = Lists.newArrayList();
        try
        {
            int count = 0;
            for (CuratorFramework client : clients) {
                LeaderLatch example = new LeaderLatch(client, PATH, "Client " + count + ": #" + IP_PORT);
                examples.add(example);
                example.start();
                count++;
            }

            System.out.println("LeaderLatch初始化完成！");
            Thread.sleep(10 * 1000);// 等待Leader选举完成
            LeaderLatch currentLeader = null;
            for (int i = 0; i < CLIENT_QTY; ++i)
            {
                LeaderLatch example = examples.get(i);
                if (example.hasLeadership()) {
                    currentLeader = example;
                }
            }

            System.out.println("当前leader：" + currentLeader.getId());
            currentLeader.close();
            examples.get(0).await(10, TimeUnit.SECONDS);
            System.out.println("当前leader：" + examples.get(0).getLeader());

            //System.out.println("输入回车退出");
            //new BufferedReader(new InputStreamReader(System.in)).readLine();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            for (LeaderLatch exampleClient : examples) {
                System.out.println("当前leader：" + exampleClient.getLeader());
                try {
                    CloseableUtils.closeQuietly(exampleClient);
                } catch (Exception e) {
                    System.out.println(exampleClient.getId() + " -- " + e.getMessage());
                }
            }
            for (CuratorFramework client : clients) {
                CloseableUtils.closeQuietly(client);
            }
        }
        System.out.println("OK!");
    }

    /**
     * InterProcessMutex类
     * Shared Reentrant Lock: 全局可重入的锁(可以多次获取，不会被阻塞)。
     * Shared意味着锁是全局可见的，客户端都可以请求锁。Reentrant和JDK的ReentrantLock类似，
     * 意味着同一个客户端在拥有锁的同时，可以多次获取，不会被阻塞。
     */
    @Test
    public void testInterProcessMutex() throws Exception {
        int QTY = 5;
        final int RTY = 3;
        final String PATH = "/examples/locks";
        //forPath函数指定创建节点的path和保存的数据，path的指定遵循linux文件path格式，创建node时指定的path，
        //父path节点需要存在，否则创建节点失败，比如创建"/parent/child"节点，若不存在节点"parent"，那么创建节点会失败。
        //client.create().forPath("/examples");
        //client.create().forPath(PATH);

        final FakeLimitedResource resource = new FakeLimitedResource();
        final List<CuratorFramework> clientList = getClientsForRealServer(QTY);
        //final List<CuratorFramework> clientList = getClientsForTest(QTY);

        System.out.println("连接初始化完成！");
        ExecutorService service = Executors.newFixedThreadPool(QTY);
        for (int i = 0; i < QTY; ++i)
        {
            final int index = i;
            Callable<Void> task = ()-> {
                try
                {
                    final ExampleClientThatLocks example = new ExampleClientThatLocks(clientList.get(index), PATH, resource, "Client " + index);
                    for (int j = 0; j < RTY; ++j)
                    {
                        //System.out.println("-----------------------------------------------------------");
                        example.doWork(10, TimeUnit.SECONDS);
                        //System.out.println("***********************************************************");
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                } finally {
                    CloseableUtils.closeQuietly(clientList.get(index));
                }
                return null;
            };
            service.submit(task);
        }
        service.shutdown();
        service.awaitTermination(10, TimeUnit.MINUTES);
        System.out.println("OK!");
    }

    /**
     * InterProcessReadWriteLock
     * 类似JDK的ReentrantReadWriteLock。一个读写锁管理一对相关的锁。一个负责读操作，另外一个负责写操作。
     * 读操作在写锁没被使用时可同时由多个进程使用，而写锁在使用时不允许读(阻塞)。
     * 此锁是可重入的。一个拥有写锁的线程可重入读锁，但是读锁却不能进入写锁。
     * 这也意味着写锁可以降级成读锁， 比如请求写锁 --->读锁 ---->释放写锁。从读锁升级成写锁是不行的。
     * @throws Exception
     */
    @Test
    public void testInterProcessReadWriteLock() throws Exception {
        int QTY = 5;
        final int RTY = 3;
        final String PATH = "/examples/locks";

        final FakeLimitedResource resource = new FakeLimitedResource();
        final List<CuratorFramework> clientList = getClientsForRealServer(QTY);
        //final List<CuratorFramework> clientList = getClientsForTest(QTY);

        System.out.println("连接初始化完成！");
        ExecutorService service = Executors.newFixedThreadPool(QTY);
        for (int i = 0; i < QTY; ++i)
        {
            final int index = i;
            Callable<Void> task = new Callable<Void>() {

                public Void call() throws Exception
                {
                    try
                    {
                        final ExampleClientReadWriteLocks example = new ExampleClientReadWriteLocks(clientList.get(index), PATH, resource, "Client " + index);
                        for (int j = 0; j < RTY; ++j)
                        {
                            //System.out.println("-----------------------------------------------------------");
                            example.doWork(10, TimeUnit.SECONDS);
                            //System.out.println("***********************************************************");
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                    } finally {
                        CloseableUtils.closeQuietly(clientList.get(index));
                    }
                    return null;
                }
            };
            service.submit(task);
        }
        service.shutdown();
        service.awaitTermination(10, TimeUnit.MINUTES);
        System.out.println("OK!");
    }

    /**
     * InterProcessMultiLock
     * Multi Shared Lock是一个锁的容器。当调用acquire，所有的锁都会被acquire，如果请求失败，所有的锁都会被release。
     * 同样调用release时所有的锁都被release(失败被忽略)。基本上，它就是组锁的代表，在它上面的请求释放操作都会传递给它包含的所有的锁。
     */
    @Test
    public void testInterProcessMultiLock() throws Exception {
        final String PATH1 = "/examples/locks1";
        final String PATH2 = "/examples/locks1";
        int QTY = 5;
        final int RTY = 3;
        final FakeLimitedResource resource = new FakeLimitedResource();
        final List<CuratorFramework> clientList = getClientsForTest(QTY);

        System.out.println("连接初始化完成！");
        ExecutorService service = Executors.newFixedThreadPool(QTY);
        for (int i = 0; i < QTY; ++i) {
            final int index = i;
            Callable<Void> task = new Callable<Void>() {

                public Void call() throws Exception
                {
                    try
                    {
                        InterProcessLock lock1 = new InterProcessMutex(clientList.get(index), PATH1); // 可重入锁
                        InterProcessLock lock2 = new InterProcessSemaphoreMutex(clientList.get(index), PATH2); // 不可重入锁
                        InterProcessMultiLock lock = new InterProcessMultiLock(Arrays.asList(lock1, lock2));
                        for (int j = 0; j < RTY; ++j)
                        {
                            if (!lock.acquire(10, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("不能获取多锁");
                            }

                            System.out.println("已获取多锁");
                            System.out.println("是否有第一个锁: " + lock1.isAcquiredInThisProcess() + " client : " + index);
                            System.out.println("是否有第二个锁: " + lock2.isAcquiredInThisProcess());

                            try {
                                resource.use(); // 资源操作
                            } catch (Exception e) {
                                e.printStackTrace();
                            } finally {
                                System.out.println("释放多个锁");
                                //System.out.println("是否有第一个锁: " + lock1.isAcquiredInThisProcess());
                                //System.out.println("是否有第二个锁: " + lock2.isAcquiredInThisProcess());
                                lock.release(); // 释放多锁
                            }
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                    } finally {
                        CloseableUtils.closeQuietly(clientList.get(index));
                    }
                    return null;
                }
            };
            service.submit(task);
        }
        service.shutdown();
        service.awaitTermination(10, TimeUnit.MINUTES);
        System.out.println("OK!");
    }

    /**
     * DistributedBarrier
     * DistributedBarrier构造函数中barrierPath参数用来确定一个栅栏，只要barrierPath参数相同(路径相同)就是同一个栅栏。通常情况下栅栏的使用如下：
     *     1.主导client设置一个栅栏
     *     2.其他客户端就会调用waitOnBarrier()等待栅栏移除，程序处理线程阻塞
     *     3.主导client移除栅栏，其他客户端的处理程序就会同时继续运行。
     * @throws Exception
     */
    @Test
    public void testDistributedBarrier() throws Exception {
        final int QTY = 5;
        final String PATH = "/examples/barrier";
        client.create().forPath(PATH);

        ExecutorService service = Executors.newFixedThreadPool(QTY);
        DistributedBarrier controlBarrier = new DistributedBarrier(client, PATH);
        controlBarrier.setBarrier();//设置栅栏
        for (int i = 0; i < QTY; ++i)
        {
            final DistributedBarrier barrier = new DistributedBarrier(client, PATH);
            final int index = i;
            Callable<Void> task = new Callable<Void>()
            {
                public Void call() throws Exception
                {
                    Thread.sleep((long) (3 * Math.random()));
                    System.out.println("Client #" + index + " 等待");
                    barrier.waitOnBarrier();//等待栅栏
                    System.out.println("Client #" + index + " 开始");
                    return null;
                }
            };
            service.submit(task);
        }

        Thread.sleep(1000 * 3);
        System.out.println("所有的Client都在等待");

        controlBarrier.removeBarrier();//删除栅栏

        service.shutdown();
        service.awaitTermination(10, TimeUnit.MINUTES);
        System.out.println("OK!");

    }

    /**
     * DistributedDoubleBarrier
     * 双栅栏允许客户端在计算的开始和结束时同步。当足够的进程加入到双栅栏时，进程开始计算，
     * 当计算完成时，离开栅栏。双栅栏类是DistributedDoubleBarrier
     */
    @Test
    public void testDistributedDoubleBarrier() throws Exception {
        final int QTY = 5;
        final String PATH = "/examples/barrier";

        ExecutorService service = Executors.newFixedThreadPool(QTY);
        for (int i = 0; i < (QTY + 2); ++i)
        {
            final DistributedDoubleBarrier barrier = new DistributedDoubleBarrier(client, PATH, QTY);
            final int index = i;
            Callable<Void> task = new Callable<Void>()
            {
                public Void call() throws Exception
                {
                    Thread.sleep((long) (3 * Math.random()));
                    System.out.println("Client #" + index + " 等待");
                    if(false == barrier.enter(5, TimeUnit.SECONDS)) {
                        System.out.println("Client #" + index + " 等待超时！");
                        return null;
                    }

                    System.out.println("Client #" + index + " 进入");
                    Thread.sleep((long) (3000 * Math.random()));
                    barrier.leave();
                    System.out.println("Client #" + index + " 结束");
                    return null;
                }
            };
            service.submit(task);
        }
        service.shutdown();
        service.awaitTermination(10, TimeUnit.MINUTES);

        System.out.println("OK!");
    }

    /**
     * SharedCounter
     * 这个类使用int类型来计数。 主要涉及三个类。
     * SharedCount - 管理一个共享的整数。所有看同样的路径客户端将有共享的整数（考虑ZK的正常一致性保证）的最高最新的值。
     * SharedCountReader - 一个共享的整数接口，并允许监听改变它的值。
     * SharedCountListener - 用于监听共享整数发生变化的监听器。
     *
     * 在这个例子中，我们使用baseCount来监听计数值(addListener方法)。任意的SharedCount，只要使用相同的PATH，都可以得到这个计数值。
     * 然后我们使用5个线程为计数值增加一个10以内的随机数。这里我们使用trySetCount去设置计数器。第一个参数提供当前的VersionedValue,
     * 如果期间其它client更新了此计数值，你的更新可能不成功，但是这时你的client更新了最新的值，所以失败了你可以尝试再更新一次。而setCount是强制更新计数器的值。
     * 注意：计数器必须start,使用完之后必须调用close关闭它。
     */
    @Test
    public void testSharedCount() throws Exception {
        final int QTY = 5;
        final String PATH = "/examples/counter";
        client.create().forPath(PATH);

        final Random rand = new Random();
        SharedCounterExample example = new SharedCounterExample();

        SharedCount baseCount = new SharedCount(client, PATH, 0);
        baseCount.addListener(example);
        baseCount.start();

        List<SharedCount> examples = Lists.newArrayList();
        ExecutorService service = Executors.newFixedThreadPool(QTY);
        for (int i = 0; i < QTY; ++i)
        {
            final SharedCount count = new SharedCount(client, PATH, 0);
            examples.add(count);
            Callable<Void> task = ()-> {
                count.start();
                Thread.sleep(rand.nextInt(10000));
                count.setCount(rand.nextInt(10));
                System.out.println("计数器当前值：" + count.getVersionedValue().getValue());
                System.out.println("计数器当前版本：" + count.getVersionedValue().getVersion());
                System.out.println("trySetCount:" + count.trySetCount(count.getVersionedValue(), 123));
                return null;
            };
            service.submit(task);
        }

        service.shutdown();
        service.awaitTermination(10, TimeUnit.MINUTES);

        for (int i = 0; i < QTY; ++i)
        {
            examples.get(i).close();
        }
        baseCount.close();

        System.out.println("OK!");
    }

    /**
     * PathCache
     * Path Cache用来监控一个ZNode的子节点。当一个子节点增加，更新，删除时，Path Cache会改变它的状态，会包含最新的子节点，子节点的数据和状态。
     * 相关实现封装：
     * PathChildrenCache - Path Cache主要实现类
     * PathChildrenCacheEvent - 监听触发时的事件对象，包含事件相关信息
     * PathChildrenCacheListener - 监听器接口
     * ChildData - 子节点数据封装类
     * @throws Exception
     */
    @Test
    public void testPathCache() throws Exception {
        final String PATH = "/example/cache";

        //forPath函数指定创建节点的path和保存的数据，path的指定遵循linux文件path格式，创建node时指定的path，
        //父path节点需要存在，否则创建节点失败，比如创建"/parent/child"节点，若不存在节点"parent"，那么创建节点会失败。
        //client.create().forPath("/example");
        client.create().forPath(PATH);

        PathChildrenCache cache = new PathChildrenCache(client, PATH, true);
        cache.start();

        cache.getListenable().addListener((cl, event)-> {
            System.out.println("事件类型：" + event.getType());
            System.out.println("节点数据：" + event.getData().getPath() + " = " + new String(event.getData().getData()));
        });


        List<String> paths = client.getChildren().forPath("/");
        System.out.println(paths);

        client.create().forPath("/example/cache/test01", "01".getBytes());
        Thread.sleep(10);

        client.create().forPath("/example/cache/test02", "02".getBytes());
        Thread.sleep(10);

        client.setData().forPath("/example/cache/test01", "01_V2".getBytes());
        Thread.sleep(10);
        for (ChildData data : cache.getCurrentData())
        {
            System.out.println("getCurrentData:" + data.getPath() + " = " + new String(data.getData()));
        }
        client.delete().forPath("/example/cache/test01");
        Thread.sleep(10);
        client.delete().forPath("/example/cache/test02");
        Thread.sleep(1000 * 5);
        cache.close();
        client.close();
        System.out.println("OK!");
    }

    /**
     * NodeCache
     * Node Cache与Path Cache类似，Node Cache只是监听某一个特定的节点。它涉及到下面的三个类：
     * NodeCache - Node Cache实现类
     * NodeCacheListener - 节点监听器
     * ChildData - 节点数据
     * @throws Exception
     */
    @Test
    public void testNodeCache() throws Exception {
        final String PATH = "/example/cache";
        //client.create().forPath("/example");

        final NodeCache cache = new NodeCache(client, PATH);
        cache.start();

        cache.getListenable().addListener(() -> {
            ChildData data = cache.getCurrentData();
            if (null != data) {
                System.out.println("节点数据：" + new String(cache.getCurrentData().getData()));
            } else {
                System.out.println("节点被删除!");
            }
        });

        List<String> paths = client.getChildren().forPath("/");
        System.out.println(paths);

        client.create().creatingParentsIfNeeded().forPath(PATH, "01".getBytes());
        Thread.sleep(10);
        client.setData().forPath(PATH, "02".getBytes());
        Thread.sleep(10);
        client.delete().deletingChildrenIfNeeded().forPath(PATH);
        Thread.sleep(1000 * 2);
        cache.close();

        System.out.println("OK!");
    }

    /**
     * Tree Node
     * Tree Node可以监控整个树上的所有节点，涉及到下面四个类。
     *** TreeCache - Tree Cache实现类
     *** TreeCacheListener - 监听器类
     *** TreeCacheEvent - 触发的事件类
     *** ChildData - 节点数据
     */
    @Test
    public void testTreeNode() throws Exception{
        final String PATH = "/example/cache";
        TreeCache cache = new TreeCache(client, PATH);
        cache.start();

        cache.getListenable().addListener((cl, event) -> {
            System.out.println("事件类型：" + event.getType() + " | 路径：" + event.getData().getPath());
        });

        client.create().forPath("/example");
        client.create().forPath("/example/cache");
        client.create().forPath("/example/cache/test01");

        client.create().creatingParentsIfNeeded().forPath("/example/cache/test01/child01");
        client.setData().forPath("/example/cache/test01", "12345".getBytes());
        client.delete().deletingChildrenIfNeeded().forPath(PATH);
        Thread.sleep(1000 * 2);
        cache.close();
        client.close();
        System.out.println("OK!");
    }

    /**
     * PersistentEphemeralNode类代表临时节点
     * 其它参数还好理解，不好理解的是PersistentEphemeralNode.Mode：
     *** EPHEMERAL： 以ZooKeeper的 CreateMode.EPHEMERAL方式创建节点。
     *** EPHEMERAL_SEQUENTIAL: 如果path已经存在，以CreateMode.EPHEMERAL创建节点，否则以CreateMode.EPHEMERAL_SEQUENTIAL方式创建节点。
     *** PROTECTED_EPHEMERAL: 以CreateMode.EPHEMERAL创建，提供保护方式。
     *** PROTECTED_EPHEMERAL_SEQUENTIAL: 类似EPHEMERAL_SEQUENTIAL，提供保护方式。
     *** 保护方式是指一种很边缘的情况：当服务器将节点创建好，但是节点名还没有返回给client,这时候服务器可能崩溃了，然后此时ZK session仍然合法，
     *** 所以此临时节点不会被删除。对于client来说，它无法知道哪个节点是它们创建的。
     * @throws Exception
     */
    @Test
    public void testPersistentEphemeralNode() throws Exception {
        final String PATH = "/example/ephemeralNode";
        final String PATH2 = "/example/node";
        client.create().forPath("/example");

        client.getConnectionStateListenable().addListener((cl, newState) -> {
            System.out.println("连接状态:" + newState.name());
        });

        PersistentEphemeralNode node = new PersistentEphemeralNode(client, PersistentEphemeralNode.Mode.EPHEMERAL, PATH, "临时节点".getBytes());
        node.start();
        node.waitForInitialCreate(3, TimeUnit.SECONDS);
        String actualPath = node.getActualPath();
        System.out.println("临时节点路径：" + actualPath + " | 值: " + new String(client.getData().forPath(actualPath)));
        client.create().forPath(PATH2, "持久化节点".getBytes());
        System.out.println("持久化节点路径： " + PATH2 + " | 值: " + new String(client.getData().forPath(PATH2)));

        KillSession.kill(client.getZookeeperClient().getZooKeeper(), "127.0.0.1:2181");
        System.out.println("临时节点路径：" + actualPath + " | 是否存在: " + (client.checkExists().forPath(actualPath) != null));
        System.out.println("持久化节点路径： " + PATH2 + " | 值: " + new String(client.getData().forPath(PATH2)));

        CloseableUtils.closeQuietly(node);
        CloseableUtils.closeQuietly(client);
    }

    /**
     * DistributedQueue
     * DistributedQueue是最普通的一种队列。它设计以下四个类：
     *** QueueBuilder - 创建队列使用QueueBuilder,它也是其它队列的创建类
     *** QueueConsumer - 队列中的消息消费者接口
     *** QueueSerializer - 队列消息序列化和反序列化接口，提供了对队列中的对象的序列化和反序列化
     *** DistributedQueue - 队列实现类
     * @throws Exception
     */
    @Test
    public void testDistributedQueue() throws Exception {
        final String PATH = "/example/queue";
        client.create().forPath("/example");
        client.create().forPath(PATH);
        final int QTY = 2;
        DistributedQueueExample example = new DistributedQueueExample();

        List<CuratorFramework> clientList = getClientsForRealServer(2);
        CuratorFramework clientA = clientList.get(0);
        CuratorFramework clientB = clientList.get(1);

        DistributedQueue<String> queueA = null;
        QueueBuilder<String> builderA = QueueBuilder.builder(clientA, example.createQueueConsumer("A"), example.createQueueSerializer(), PATH);
        queueA = builderA.buildQueue();
        queueA.start();

        DistributedQueue<String> queueB = null;
        QueueBuilder<String> builderB = QueueBuilder.builder(clientB, example.createQueueConsumer("B"), example.createQueueSerializer(), PATH);
        queueB = builderB.buildQueue();
        queueB.start();

        for (int i = 0; i < 100; i++)
        {
            queueA.put(" test-A-" + i);
            Thread.sleep(10);
            queueB.put(" test-B-" + i);
        }

        Thread.sleep(1000 * 50);// 等待消息消费完成
        queueB.close();
        queueA.close();
        clientB.close();
        clientA.close();
        System.out.println("OK!");
    }

    /**
     * use by testDistributedQueue
     */
    class DistributedQueueExample {
        /** 队列消息序列化实现类 */
        public QueueSerializer<String> createQueueSerializer() {
            return new QueueSerializer<String>() {
                @Override
                public byte[] serialize(String item)
                {
                    return item.getBytes();
                }
                @Override
                public String deserialize(byte[] bytes)
                {
                    return new String(bytes);
                }
            };
        }

        /** 定义队列消费者 */
        public QueueConsumer<String> createQueueConsumer(final String name) {
            return new QueueConsumer<String>() {
                @Override
                public void stateChanged(CuratorFramework client, ConnectionState newState)
                {
                    System.out.println("连接状态改变: " + newState.name());
                }
                @Override
                public void consumeMessage(String message) throws Exception
                {
                    System.out.println("消费消息(" + name + "): " + message);
                }
            };
        }
    }
}