/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package base;

import java.util.UUID;
import org.apache.rocketmq.broker.BrokerController;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.MQVersion;
import org.apache.rocketmq.namesrv.NamesrvController;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.test.listener.AbstractListener;
import org.apache.rocketmq.test.util.MQRandomUtils;
import org.apache.rocketmq.test.util.RandomUtil;

public final class BaseConf {
  public static final String nsAddr;
  public static final String broker1Addr;
  protected static String broker1Name;
  protected static final String clusterName;
  protected static final NamesrvController namesrvController;
  protected static final BrokerController brokerController1;

  static {
    System.setProperty(
        RemotingCommand.REMOTING_VERSION_KEY, Integer.toString(MQVersion.CURRENT_VERSION));
    namesrvController = IntegrationTestBase.createAndStartNamesrv();
    nsAddr = "localhost:" + namesrvController.getNettyServerConfig().getListenPort();
    brokerController1 = IntegrationTestBase.createAndStartBroker(nsAddr);
    clusterName = brokerController1.getBrokerConfig().getBrokerClusterName();
    broker1Name = brokerController1.getBrokerConfig().getBrokerName();
    broker1Addr = "localhost:" + brokerController1.getNettyServerConfig().getListenPort();
  }

  private BaseConf() {}

  public static String initTopic() {
    String topic = MQRandomUtils.getRandomTopic();
    IntegrationTestBase.initTopic(topic, nsAddr, clusterName);
    return topic;
  }

  public static DefaultMQPushConsumer getConsumer(
      String nsAddr, String topic, String subExpression, AbstractListener listener)
      throws MQClientException {
    DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("consumerGroup");
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
