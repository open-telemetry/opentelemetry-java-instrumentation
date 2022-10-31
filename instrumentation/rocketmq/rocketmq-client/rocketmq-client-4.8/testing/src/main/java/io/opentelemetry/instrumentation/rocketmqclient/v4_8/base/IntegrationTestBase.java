/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8.base;

import static java.util.Collections.emptyMap;

import io.opentelemetry.instrumentation.test.utils.PortUtils;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.rocketmq.broker.BrokerController;
import org.apache.rocketmq.common.BrokerConfig;
import org.apache.rocketmq.common.namesrv.NamesrvConfig;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.logging.InternalLoggerFactory;
import org.apache.rocketmq.namesrv.NamesrvController;
import org.apache.rocketmq.remoting.netty.NettyClientConfig;
import org.apache.rocketmq.remoting.netty.NettyServerConfig;
import org.apache.rocketmq.store.config.MessageStoreConfig;
import org.junit.Assert;

public final class IntegrationTestBase {
  public static final InternalLogger logger =
      InternalLoggerFactory.getLogger(IntegrationTestBase.class);

  static final String BROKER_NAME_PREFIX = "TestBrokerName_";
  static final AtomicInteger BROKER_INDEX = new AtomicInteger(0);
  static final List<File> TMPE_FILES = new ArrayList<>();
  static final List<BrokerController> BROKER_CONTROLLERS = new ArrayList<>();
  static final List<NamesrvController> NAMESRV_CONTROLLERS = new ArrayList<>();
  static final int COMMIT_LOG_SIZE = 1024 * 1024 * 100;
  static final int INDEX_NUM = 1000;

  private static String createTempDir() {
    String path = null;
    try {
      File file = Files.createTempDirectory("opentelemetry-rocketmq-client-temp").toFile();
      TMPE_FILES.add(file);
      path = file.getCanonicalPath();
    } catch (IOException e) {
      logger.warn("Error creating temporary directory.", e);
    }
    return path;
  }

  public static void deleteTempDir() {
    for (File file : TMPE_FILES) {
      boolean deleted = file.delete();
      if (!deleted) {
        file.deleteOnExit();
      }
    }
  }

  public static NamesrvController createAndStartNamesrv() {
    String baseDir = createTempDir();
    Path kvConfigPath = Paths.get(baseDir, "namesrv", "kvConfig.json");
    Path namesrvPath = Paths.get(baseDir, "namesrv", "namesrv.properties");

    NamesrvConfig namesrvConfig = new NamesrvConfig();
    NettyServerConfig nameServerNettyServerConfig = new NettyServerConfig();

    namesrvConfig.setKvConfigPath(kvConfigPath.toString());
    namesrvConfig.setConfigStorePath(namesrvPath.toString());

    // find 3 consecutive open ports and use the last one of them
    // rocketmq will also bind to given port - 2
    nameServerNettyServerConfig.setListenPort(PortUtils.findOpenPorts(3) + 2);
    NamesrvController namesrvController =
        new NamesrvController(namesrvConfig, nameServerNettyServerConfig);
    try {
      Assert.assertTrue(namesrvController.initialize());
      logger.info("Name Server Start:{}", nameServerNettyServerConfig.getListenPort());
      namesrvController.start();
    } catch (Exception e) {
      logger.info("Name Server start failed", e);
    }
    NAMESRV_CONTROLLERS.add(namesrvController);
    return namesrvController;
  }

  public static BrokerController createAndStartBroker(String nsAddr) {
    String baseDir = createTempDir();
    Path commitLogPath = Paths.get(baseDir, "commitlog");

    BrokerConfig brokerConfig = new BrokerConfig();
    MessageStoreConfig storeConfig = new MessageStoreConfig();
    brokerConfig.setBrokerName(BROKER_NAME_PREFIX + BROKER_INDEX.getAndIncrement());
    brokerConfig.setBrokerIP1("127.0.0.1");
    brokerConfig.setNamesrvAddr(nsAddr);
    brokerConfig.setEnablePropertyFilter(true);
    storeConfig.setStorePathRootDir(baseDir);
    storeConfig.setStorePathCommitLog(commitLogPath.toString());
    storeConfig.setMappedFileSizeCommitLog(COMMIT_LOG_SIZE);
    storeConfig.setMaxIndexNum(INDEX_NUM);
    storeConfig.setMaxHashSlotNum(INDEX_NUM * 4);
    return createAndStartBroker(storeConfig, brokerConfig);
  }

  public static BrokerController createAndStartBroker(
      MessageStoreConfig storeConfig, BrokerConfig brokerConfig) {
    NettyServerConfig nettyServerConfig = new NettyServerConfig();
    NettyClientConfig nettyClientConfig = new NettyClientConfig();
    nettyServerConfig.setListenPort(PortUtils.findOpenPort());
    storeConfig.setHaListenPort(PortUtils.findOpenPort());
    BrokerController brokerController =
        new BrokerController(brokerConfig, nettyServerConfig, nettyClientConfig, storeConfig);
    try {
      Assert.assertTrue(brokerController.initialize());
      logger.info(
          "Broker Start name:{} addr:{}",
          brokerConfig.getBrokerName(),
          brokerController.getBrokerAddr());
      brokerController.start();
    } catch (Throwable t) {
      logger.error("Broker start failed", t);
      throw new IllegalStateException("Broker start failed", t);
    }
    BROKER_CONTROLLERS.add(brokerController);
    return brokerController;
  }

  public static void initTopic(String topic, String nsAddr, String clusterName) {
    try {
      // RocketMQ 4.x
      Class<?> mqAdmin = Class.forName("org.apache.rocketmq.test.util.MQAdmin");
      Method createTopic =
          mqAdmin.getMethod("createTopic", String.class, String.class, String.class, int.class);
      createTopic.invoke(null, nsAddr, clusterName, topic, 20);
    } catch (ClassNotFoundException
        | InvocationTargetException
        | NoSuchMethodException
        | IllegalAccessException e) {

      // RocketMQ 5.x
      try {
        Class<?> mqAdmin = Class.forName("org.apache.rocketmq.test.util.MQAdminTestUtils");
        Method createTopic =
            mqAdmin.getMethod(
                "createTopic", String.class, String.class, String.class, int.class, Map.class);
        createTopic.invoke(null, nsAddr, clusterName, topic, 20, emptyMap());
      } catch (ClassNotFoundException
          | InvocationTargetException
          | NoSuchMethodException
          | IllegalAccessException ex) {
        throw new LinkageError("Could not initialize topic", ex);
      }
    }
  }

  private IntegrationTestBase() {}
}
