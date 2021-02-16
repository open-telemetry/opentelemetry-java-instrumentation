/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package base.dledger;

import static base.IntegrationTestBase.nextPort;

import base.BaseConf;
import base.IntegrationTestBase;
import java.util.UUID;
import org.apache.rocketmq.broker.BrokerController;
import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.client.consumer.PullResult;
import org.apache.rocketmq.client.consumer.PullStatus;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.BrokerConfig;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.store.config.BrokerRole;
import org.apache.rocketmq.store.config.MessageStoreConfig;
import org.apache.rocketmq.test.factory.ConsumerFactory;
import org.apache.rocketmq.test.factory.ProducerFactory;
import org.junit.Assert;
import org.junit.Test;

public class DLedgerProduceAndConsumeIT {

    public BrokerConfig buildBrokerConfig(String cluster, String brokerName) {
        BrokerConfig brokerConfig =  new BrokerConfig();
        brokerConfig.setBrokerClusterName(cluster);
        brokerConfig.setBrokerName(brokerName);
        brokerConfig.setBrokerIP1("127.0.0.1");
        brokerConfig.setNamesrvAddr(BaseConf.nsAddr);
        return brokerConfig;
    }

    public MessageStoreConfig buildStoreConfig(String brokerName, String peers, String selfId) {
        MessageStoreConfig storeConfig = new MessageStoreConfig();
        String baseDir =  IntegrationTestBase.createBaseDir();
        storeConfig.setStorePathRootDir(baseDir);
        storeConfig.setStorePathCommitLog(baseDir + "_" + "commitlog");
        storeConfig.setHaListenPort(nextPort());
        storeConfig.setMappedFileSizeCommitLog(10 * 1024 * 1024);
        storeConfig.setEnableDLegerCommitLog(true);
        storeConfig.setdLegerGroup(brokerName);
        storeConfig.setdLegerSelfId(selfId);
        storeConfig.setdLegerPeers(peers);
        return storeConfig;
    }

}
