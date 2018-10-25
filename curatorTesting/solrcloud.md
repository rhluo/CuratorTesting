#### 一、zookeeper安装
1. 版本：zookeeper-3.5.3-beta
2. 下载zookeeper-3.5.3-beta.tar.gz<br/>
   地址：https://mirrors.tuna.tsinghua.edu.cn/apache/zookeeper/
3. 解压到相应的文件夹

        tar -zxvf zookeeper-3.5.3-beta.tar.gz -C /home/luoronghua/
4. 创建目录

        cd /home/luoronghua/zookeeper-3.5.3-beta<br/>
        mkdir data #数据存放<br/>
        mkdir logs #日志存放
5. 修改配置

    cd /home/luoronghua/zookeeper-3.5.3-beta/conf<br/>
    mv zoo_sample.cfg zoo.cfg #修改zoo.cfg
    > dataDir=/home/luoronghua/zookeeper-3.5.3-beta/data<br/>
    > dataLogDir=/home/luoronghua/zookeeper-3.5.3-beta/logs<br/>
    > server.1=127.0.0.1:2888:3888
6. 运行

    ./zkServer.sh start #启动zookeeper
    ./zkServer.sh status #查看zookeeper状态
    ./zkServer.sh stop #关闭zookeeper

#### 二、tomcat安装
1. 版本：apache-tomcat-8.5.34
2. 下载tomcat，解压，启动（略）
3. 这里需要在目录下创建（拷贝）多份tomcat，需要修改相应的端口号

        在tomcat目录下的conf内server.xml文件，修改8080端口为相应自己的端口（非伪集群下，可以直接使用8080端口）<br/>
        我们这里将端口依次设为8081,8082,8083（三个tomcat服务器编号1-3）<br/>
        注意在伪集群模式下，需要将server.xml里面其他端口依次修改一下，否则一个服务器无法启动多个tomcat<br/>

#### 三、solrcloud安装
特别说明：这里solrcloud安装为伪集群模式，安装集群模式只需更改相应的IP_PORT即可
这里以tomcat1为例做说明
1. 版本：solr-6.6.5
2. 下载solr<br/>
   注意下载：solr-6.6.5.tgz(tgz文件)
3. 将solr部署到tomcat上

        cp -R **/solr/server/solr-webapp/webapp  **/tomcat1/webapp/
        mv **/tomcat1/weapps/webapp/ **/tomcat1/weapps/solr #重命名为solr

4. 复制相关依赖包到tomcat

        cp **/solr/server/lib/*.jar solr/WEB-INF/lib/
        cp **/solr/server/lib/ext/*.jar solr/WEB-INF/lib/
5. 复制log4j到tomcat

        mkdir **/tomcat1/weapps/solr/WEB-INF/classes
        cp **/solr/server/resources/log4j.properties  **/tomcat1/weapps/solr/WEB-INF/classes/
6. 创建solrcloud的统一配置存放目录solrconf以及solrhome

        伪集群需要创建三个solrhome
        mkdir -p **/solrbase/conf
        mkdir -p **/solrbase/solrhome1
        mkdir -p **/solrbase/solrhome2
        mkdir -p **/solrbase/solrhome3

7. 复制配置文件和lib 到solrcloud对应目录下

        cp -r **/solr/server/solr/configsets/basic_configs/conf/*   **/solrbase/conf/
8. 复制solr.xml 和 zoo.cfg到solrhome目录下

        cp **/solr/server/solr/solr.xml /usr/local/solr/server/solr/zoo.cfg   **/solrbase/solrhome
9. 修改solr.xml

            cd /home/luoronghua/solrbase/solrhome1
            <str name="host">172.22.0.17</str>
            <int name="hostPort">8081</int>#hostport为对应接口,对于solr2（在tomcat2上运行的版本就是8082）

以下为tomcat上运行的solr的修改内容

10. 修改web.xml

        vim /home/luoronghua/solr-tomcat/tomcat1/webapps/solr/WEB-INF/web.xml
    >     <env-entry>
    >         <env-entry-name>solr/home</env-entry-name>
    >         <env-entry-value>/home/luoronghua/solrbase/solrhome1</env-entry-value>
    >         <env-entry-type>java.lang.String</env-entry-type>
    >      </env-entry>
11. 修改tomcat的startup.sh文件以及shutdown.sh文件

        vim /home/luoronghua/solr-tomcat/tomcat1/bin/startup.sh
        vim /home/luoronghua/solr-tomcat/tomcat1/bin/shutdown.sh
    > export CATALINA_HOME=/home/luoronghua/solr-tomcat/tomcat1<br/>
    > export CATALINA_BASE=/home/luoronghua/solr-tomcat/tomcat1

12. 修改catalina.sh文件

        vim /home/luoronghua/solr-tomcat/tomcat1/bin/catalina.sh
    > JAVA_OPTS="-DzkHost=127.0.0.1:2181"

同理修改另外两个tomcat服务器上的配置

13. 上传配置信息到zookeeper

        java -classpath .:/home/luoronghua/solr-tomcat/tomcat1/webapps/solr/WEB-INF/lib/* org.apache.solr.cloud.ZkCLI -cmd upconfig -zkhost 127.0.0.1:2181 -confdir /home/luoronghua/solrbase/conf/ -confname solrconfig

14. 启动

        先启动zookeeper
        然后逐个启动tomcat
15. 验证

        http://172.22.0.17:8081/solr/index.html
        注意solr6以后的一个bug，没有加index.html会报404
        使用url地址调用相应的api做验证

@author: luoronghua<br/>
@email: luoronghua17s@ict.ac.cn<br/>
@date: 2018/10/24<br/>