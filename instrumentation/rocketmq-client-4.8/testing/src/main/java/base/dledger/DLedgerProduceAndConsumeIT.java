/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package base.dledger;

import static base.IntegrationTestBase.nextPort;

import base.BaseConf;
import base.IntegrationTestBase;
import org.apache.rocketmq.common.BrokerConfig;
import org.apache.rocketmq.store.config.MessageStoreConfig;

public class DLedgerProduceAndConsumeIT {

  public BrokerConfig buildBrokerConfig(String cluster, String brokerName) {
    BrokerConfig brokerConfig = new BrokerConfig();
    brokerConfig.setBrokerClusterName(cluster);
    brokerConfig.setBrokerName(brokerName);
    brokerConfig.setBrokerIP1("127.0.0.1");
    brokerConfig.setNamesrvAddr(BaseConf.nsAddr);
    return brokerConfig;
  }

  public MessageStoreConfig buildStoreConfig(String brokerName, String peers, String selfId) {
    MessageStoreConfig storeConfig = new MessageStoreConfig();
    String baseDir = IntegrationTestBase.createBaseDir();
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
