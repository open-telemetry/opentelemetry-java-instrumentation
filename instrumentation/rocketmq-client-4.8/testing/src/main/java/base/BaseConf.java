/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package base;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.log4j.Logger;
import org.apache.rocketmq.broker.BrokerController;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.MQVersion;
import org.apache.rocketmq.namesrv.NamesrvController;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.test.listener.AbstractListener;
import org.apache.rocketmq.test.util.MQAdmin;
import org.apache.rocketmq.test.util.MQRandomUtils;
import org.apache.rocketmq.test.util.RandomUtil;

final class BaseConf {
  public static final String nsAddr;
  public static final String broker1Addr;
  protected static String broker1Name;
  protected static final String clusterName;
  protected static int brokerNum;
  protected static final NamesrvController namesrvController;
  protected static final BrokerController brokerController1;
  protected static final List<Object> mqClients = new ArrayList<Object>();
  protected static boolean debug = false;
  private static final Logger log = Logger.getLogger(BaseConf.class);

  static {
    System.setProperty(
        RemotingCommand.REMOTING_VERSION_KEY, Integer.toString(MQVersion.CURRENT_VERSION));
    namesrvController = IntegrationTestBase.createAndStartNamesrv();
    nsAddr = "127.0.0.1:" + namesrvController.getNettyServerConfig().getListenPort();
    brokerController1 = IntegrationTestBase.createAndStartBroker(nsAddr);
    clusterName = brokerController1.getBrokerConfig().getBrokerClusterName();
    broker1Name = brokerController1.getBrokerConfig().getBrokerName();
    broker1Addr = "127.0.0.1:" + brokerController1.getNettyServerConfig().getListenPort();
    brokerNum = 2;
  }

  private BaseConf() {}

  public static String initTopic() {
    String topic = MQRandomUtils.getRandomTopic();
    if (!IntegrationTestBase.initTopic(topic, nsAddr, clusterName)) {
      log.error("Topic init failed");
    }
    return topic;
  }

  public static String getBrokerAddr() {
    return broker1Addr;
  }

  public static String initConsumerGroup() {
    String group = MQRandomUtils.getRandomConsumerGroup();
    return initConsumerGroup(group);
  }

  public static String initConsumerGroup(String group) {
    MQAdmin.createSub(nsAddr, clusterName, group);
    return group;
  }

  public static DefaultMQPushConsumer getConsumer(
      String nsAddr, String topic, String subExpression, AbstractListener listener)
      throws MQClientException {
    String consumerGroup = initConsumerGroup();
    DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(consumerGroup);
    consumer.setInstanceName(RandomUtil.getStringByUUID());
    consumer.setNamesrvAddr(nsAddr);
    consumer.subscribe(topic, subExpression);
    consumer.setMessageListener(listener);
    consumer.start();
    return consumer;
  }

  public static DefaultMQProducer getProducer(String ns) throws MQClientException {
    DefaultMQProducer producer = new DefaultMQProducer(RandomUtil.getStringByUUID());
    producer.setInstanceName(UUID.randomUUID().toString());
    producer.setNamesrvAddr(ns);
    producer.start();
    return producer;
  }

  private static void deleteTempDir() {
    IntegrationTestBase.deleteTempDir();
  }
}
