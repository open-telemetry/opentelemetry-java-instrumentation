/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package base;

import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Logger;
import org.apache.rocketmq.broker.BrokerController;
import org.apache.rocketmq.client.producer.TransactionListener;
import org.apache.rocketmq.common.MQVersion;
import org.apache.rocketmq.namesrv.NamesrvController;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.test.client.rmq.RMQAsyncSendProducer;
import org.apache.rocketmq.test.client.rmq.RMQNormalConsumer;
import org.apache.rocketmq.test.client.rmq.RMQNormalProducer;
import org.apache.rocketmq.test.client.rmq.RMQTransactionalProducer;
import org.apache.rocketmq.test.clientinterface.AbstractMQConsumer;
import org.apache.rocketmq.test.clientinterface.AbstractMQProducer;
import org.apache.rocketmq.test.factory.ConsumerFactory;
import org.apache.rocketmq.test.listener.AbstractListener;
import org.apache.rocketmq.test.util.MQAdmin;
import org.apache.rocketmq.test.util.MQRandomUtils;

public class BaseConf {
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

  public BaseConf() {}

  public static String initTopic() {
    String topic = MQRandomUtils.getRandomTopic();
    IntegrationTestBase.initTopic(topic, nsAddr, clusterName);

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

  public static RMQNormalProducer getProducer(String nsAddr, String topic) {
    return getProducer(nsAddr, topic, false);
  }

  public static RMQNormalProducer getProducer(String nsAddr, String topic, boolean useTls) {
    RMQNormalProducer producer = new RMQNormalProducer(nsAddr, topic, useTls);
    if (debug) {
      producer.setDebug();
    }
    mqClients.add(producer);
    return producer;
  }

  public static RMQNormalProducer getProducer(
      String nsAddr, String topic, String producerGoup, String instanceName) {
    RMQNormalProducer producer = new RMQNormalProducer(nsAddr, topic, producerGoup, instanceName);
    if (debug) {
      producer.setDebug();
    }
    mqClients.add(producer);
    return producer;
  }

  public static RMQTransactionalProducer getTransactionalProducer(
      String nsAddr, String topic, TransactionListener transactionListener) {
    RMQTransactionalProducer producer =
        new RMQTransactionalProducer(nsAddr, topic, false, transactionListener);
    if (debug) {
      producer.setDebug();
    }
    mqClients.add(producer);
    return producer;
  }

  public static RMQAsyncSendProducer getAsyncProducer(String nsAddr, String topic) {
    RMQAsyncSendProducer producer = new RMQAsyncSendProducer(nsAddr, topic);
    if (debug) {
      producer.setDebug();
    }
    mqClients.add(producer);
    return producer;
  }

  public static RMQNormalConsumer getConsumer(
      String nsAddr, String topic, String subExpression, AbstractListener listener) {
    return getConsumer(nsAddr, topic, subExpression, listener, false);
  }

  public static RMQNormalConsumer getConsumer(
      String nsAddr,
      String topic,
      String subExpression,
      AbstractListener listener,
      boolean useTls) {
    String consumerGroup = initConsumerGroup();
    return getConsumer(nsAddr, consumerGroup, topic, subExpression, listener, useTls);
  }

  public static RMQNormalConsumer getConsumer(
      String nsAddr,
      String consumerGroup,
      String topic,
      String subExpression,
      AbstractListener listener) {
    return getConsumer(nsAddr, consumerGroup, topic, subExpression, listener, false);
  }

  public static RMQNormalConsumer getConsumer(
      String nsAddr,
      String consumerGroup,
      String topic,
      String subExpression,
      AbstractListener listener,
      boolean useTls) {
    RMQNormalConsumer consumer =
        ConsumerFactory.getRMQNormalConsumer(
            nsAddr, consumerGroup, topic, subExpression, listener, useTls);
    if (debug) {
      consumer.setDebug();
    }
    mqClients.add(consumer);
    log.info(
        String.format(
            "consumer[%s] start,topic[%s],subExpression[%s]", consumerGroup, topic, subExpression));
    return consumer;
  }

  public static void shutdown() {
    try {
      for (Object mqClient : mqClients) {
        if (mqClient instanceof AbstractMQProducer) {
          ((AbstractMQProducer) mqClient).shutdown();

        } else {
          ((AbstractMQConsumer) mqClient).shutdown();
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
