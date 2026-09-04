/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hadoop.conf.Configuration;
import org.junit.jupiter.api.Test;

class HbaseServerTarget24Test {

  private static final String REGISTRY_KEY = "hbase.client.registry.impl";
  private static final String MASTER_REGISTRY = "org.apache.hadoop.hbase.client.MasterRegistry";

  @Test
  void rendersConfiguredMasterRegistryTarget() {
    Configuration configuration = new Configuration(false);
    configuration.set(REGISTRY_KEY, MASTER_REGISTRY);
    configuration.set("hbase.masters", "master-b,master-a");
    configuration.set("hbase.master.port", "17000");

    assertThat(HbaseServerTarget.from(configuration)).isEqualTo("master-a:17000,master-b:17000");
  }

  @Test
  void omitsUnconfiguredMasterRegistryTarget() {
    Configuration configuration = new Configuration(false);
    configuration.set(REGISTRY_KEY, MASTER_REGISTRY);

    assertThat(HbaseServerTarget.from(configuration)).isNull();
  }

  @Test
  void prefersClientZooKeeperConfiguration() {
    Configuration configuration = new Configuration(false);
    configuration.set("hbase.zookeeper.quorum", "server-zk");
    configuration.set("hbase.zookeeper.property.clientPort", "2182");
    configuration.set("hbase.client.zookeeper.quorum", "client-zk");
    configuration.set("hbase.client.zookeeper.property.clientPort", "2183");

    assertThat(HbaseServerTarget.from(configuration)).isEqualTo("client-zk:2183:/hbase");
  }
}
