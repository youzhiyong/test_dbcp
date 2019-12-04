package com.yzy.test;/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import javax.sql.DataSource;

//
// Here are the dbcp-specific classes.
// Note that they are only used in the setupDataSource
// method. In normal use, your classes interact
// only with the standard JDBC API
//
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.dbcp2.PoolableConnection;
import org.apache.commons.pool2.impl.GenericObjectPool;

//
// Here's a simple example of how to use the BasicDataSource.
//

//
// Note that this example is very similar to the PoolingDriver
// example.

//
// To compile this example, you'll want:
//  * commons-pool-2.3.jar
//  * commons-dbcp-2.1.jar 
// in your classpath.
//
// To run this example, you'll want:
//  * commons-pool-2.3.jar
//  * commons-dbcp-2.1.jar 
//  * commons-logging-1.2.jar
// in your classpath.
//
//
// Invoke the class using two arguments:
//  * the connect string for your underlying JDBC driver
//  * the query you'd like to execute
// You'll also want to ensure your underlying JDBC driver
// is registered.  You can use the "jdbc.drivers"
// property to do this.
//
// For example:
//  java -Djdbc.drivers=org.h2.Driver \
//       -classpath commons-pool2-2.3.jar:commons-dbcp2-2.1.jar:commons-logging-1.2.jar:h2-1.3.152.jar:. \
//       BasicDataSourceExample \
//       "jdbc:h2:~/test" \
//       "SELECT 1"
//
public class BasicDataSourceExample {

    public static void main(String[] args) throws InterruptedException, BrokenBarrierException {
        // First we set up the BasicDataSource.
        // Normally this would be handled auto-magically by
        // an external configuration, but in this example we'll
        // do it manually.
        //
        System.out.println("Setting up data source.");
        String url = "jdbc:mysql://localhost:3306/auth?serverTimezone=UTC&characterEncoding=utf-8&characterSetResults=utf-8&useUnicode=false&rewriteBatchedStatements=true&rewriteBatchedStatements=true";
        DataSource dataSource = setupDataSource(url);
        System.out.println("Done.");

        //
        // Now, we can use JDBC DataSource as we normally would.
        //
        int n = 10;
        CyclicBarrier cyclicBarrier = new CyclicBarrier(n + 1);
        while (n > 0) {
            new Thread(() -> {

                Connection conn = null;
                Statement stmt = null;
                ResultSet rset = null;
                try {
                    System.out.println("Creating connection.");
                    conn = dataSource.getConnection();
                    System.out.println("Creating statement.");
                    stmt = conn.createStatement();
                    System.out.println("Executing statement.");
                    rset = stmt.executeQuery("select * from authorization");
                    /*System.out.println("Results:");
                    int numcols = rset.getMetaData().getColumnCount();
                    while(rset.next()) {
                        for(int i=1;i<=numcols;i++) {
                            System.out.print("\t" + rset.getString(i));
                        }
                        System.out.println("");
                    }*/
                    printDataSourceInfo(dataSource);
                } catch(Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        cyclicBarrier.await();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    System.out.println("------------finally---------");
                    try { if (rset != null) rset.close(); } catch(Exception e) { }
                    try { if (stmt != null) stmt.close(); } catch(Exception e) { }
                    try { if (conn != null) conn.close(); } catch(Exception e) { }
                }
            }).start();
            Thread.sleep(1000);
            n--;
        }

        //观察一段时间不使用连接池后，连接池的变化情况
        cyclicBarrier.await();
        Thread.sleep(1000);
        int t = 35;
        while (t > 0) {
            t--;
            System.out.println("----------------" + (35 - t));
            printDataSourceInfo(dataSource);
            Thread.sleep(1000);
        }

    }

    private static void printDataSourceInfo(DataSource dataSource) {

        System.out.println("===========================================================");
        GenericObjectPool<PoolableConnection> connectionGenericObjectPool = ((BasicDataSource)dataSource).getConnectPool();
        System.out.println("NumActive: " + connectionGenericObjectPool.getNumActive());
        System.out.println("NumIdle: " + connectionGenericObjectPool.getNumIdle());
        System.out.println("getCreatedCount:" + connectionGenericObjectPool.getCreatedCount());
        System.out.println("getBorrowedCount:" + connectionGenericObjectPool.getBorrowedCount());
        System.out.println("getDestroyedCount:" + connectionGenericObjectPool.getDestroyedCount());
        System.out.println("getDestroyedByEvictorCount:" + connectionGenericObjectPool.getDestroyedByEvictorCount());
        System.out.println("getDestroyedByBorrowValidationCount:" + connectionGenericObjectPool.getDestroyedByBorrowValidationCount());
    }

    /**
     *
     * 查看数据库的最大连接数
     * show variables like '%max_connections%';
     * 设置最大连接数，立即生效，数据库重启后失效
     * set GLOBAL max_connections = 25;
     * @param connectURI
     * @return
     */
    public static DataSource setupDataSource(String connectURI) {
        BasicDataSource ds = new BasicDataSource();
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setUrl(connectURI);
        ds.setPassword("root");
        ds.setUsername("root");

        // 第一次请求获取连接时 会 初始化 InitialSize这么多连接
        ds.setInitialSize(3);

        // 最大空闲连接数
        ds.setMaxIdle(5);
        // 最小空闲连接数
        ds.setMinIdle(3);
        // 最大连接数  连接池的连接不够用时，最大能创建的连接数
        ds.setMaxTotal(10);

        //注意 MaxIdle 和 MaxTotal
        //MaxIdle 表示最大的空闲连接数，MaxTotal表示能借出去的最大连接数

        // 验证一个连接是否可用   数据库层面可以主动来关闭一个连接
        ds.setValidationQuery("select 1");

        //获取一个连接的最大等待时间   当连接池中的连接达到最大值后，并且都被借用未归还，后面想获取连接的线程需要等待其他线程归还连接，这里是设置这个等待时间
        //ds.setMaxWaitMillis(10 * 1000);  //  单位毫秒

        // 这三个参数相当于设置一个 借用连接的 最长时间，当一个线程借用一个连接后在规定时间内还未归还，则直接将这个连接 销毁 (不是收回)
        //ds.setRemoveAbandonedOnBorrow(true);  // 是否开启这个机制
        //ds.setRemoveAbandonedTimeout(10);    //借用的最长时间  单位秒
        //ds.setLogAbandoned(true);

        // 这两个参数是用于清除处于空闲的连接 使其空闲连接数到达 最小 即达到minIdle
        ds.setTimeBetweenEvictionRunsMillis(10 * 1000);  //执行清楚检查的时间间隔
        ds.setMinEvictableIdleTimeMillis(15 * 1000);     //定义空间多久需要被清除，

        //ds.setTestWhileIdle(true);


        return ds;
    }
}
