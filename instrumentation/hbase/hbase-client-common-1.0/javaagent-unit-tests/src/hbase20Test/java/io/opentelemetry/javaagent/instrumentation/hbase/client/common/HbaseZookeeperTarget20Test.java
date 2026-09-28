/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.zookeeper.ZKConfig;
import org.junit.jupiter.api.Test;

class HbaseZookeeperTarget20Test {

  @Test
  void matchesHbaseZooKeeperClusterKey() {
    Configuration configuration = new Configuration(false);
    configuration.set("hbase.zookeeper.quorum", "zk-a,zk-b");
    configuration.set("hbase.zookeeper.property.clientPort", "2182");
    configuration.set("zookeeper.znode.parent", "/production");

    assertThat(HbaseZookeeperTarget.from(configuration))
        .isEqualTo(ZKConfig.getZooKeeperClusterKey(configuration));
  }
}
