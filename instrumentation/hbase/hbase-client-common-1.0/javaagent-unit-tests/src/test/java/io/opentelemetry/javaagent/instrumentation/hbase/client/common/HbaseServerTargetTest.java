/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hadoop.conf.Configuration;
import org.junit.jupiter.api.Test;

class HbaseServerTargetTest {

  private static final String REGISTRY_KEY = "hbase.client.registry.impl";
  private static final String ZK_ASYNC_REGISTRY = "org.apache.hadoop.hbase.client.ZKAsyncRegistry";
  private static final String ZK_REGISTRY = "org.apache.hadoop.hbase.client.ZKConnectionRegistry";
  private static final String MASTER_REGISTRY = "org.apache.hadoop.hbase.client.MasterRegistry";

  @Test
  void rendersZooKeeperDefaults() {
    assertThat(HbaseServerTarget.from(new Configuration(false))).isEqualTo("localhost:2181:/hbase");
  }

  @Test
  void fallsBackToHbaseConfigurationWhenZooCfgIsAbsent() {
    Configuration configuration = new Configuration(false);
    configuration.setBoolean("hbase.config.read.zookeeper.config", true);
    configuration.set("hbase.zookeeper.quorum", "active-zk");

    assertThat(HbaseServerTarget.from(configuration)).isEqualTo("active-zk:2181:/hbase");
  }

  @Test
  void ignoresZooCfgFlagWhenUnsupported() {
    Configuration configuration = new Configuration(false);
    configuration.setBoolean("hbase.config.read.zookeeper.config", true);
    configuration.set("hbase.zookeeper.quorum", "active-zk");

    assertThat(HbaseServerTarget.from(configuration, false, false, false, false))
        .isEqualTo("active-zk:2181:/hbase");
  }

  @Test
  void rendersCanonicalZooKeeperClusterKey() {
    Configuration configuration = new Configuration(false);
    configuration.set(REGISTRY_KEY, ZK_REGISTRY);
    configuration.set("hbase.zookeeper.quorum", " zk-b:2222,zk-a,zk-b:2222 ");
    configuration.set("hbase.zookeeper.property.clientPort", "3218");
    configuration.set("zookeeper.znode.parent", "/production");

    assertThat(HbaseServerTarget.from(configuration, false, true, false))
        .isEqualTo("zk-a,zk-b:2222:3218:/production");
  }

  @Test
  void recognizesTheEarlierZooKeeperRegistryClass() {
    Configuration configuration = new Configuration(false);
    configuration.set(REGISTRY_KEY, ZK_ASYNC_REGISTRY);
    configuration.set("hbase.zookeeper.quorum", "zk");

    assertThat(HbaseServerTarget.from(configuration, false, true, false))
        .isEqualTo("zk:2181:/hbase");
  }

  @Test
  void prefersClientZooKeeperConfigurationWhenSupported() {
    Configuration configuration = new Configuration(false);
    configuration.set("hbase.zookeeper.quorum", "server-zk");
    configuration.set("hbase.zookeeper.property.clientPort", "2182");
    configuration.set("hbase.client.zookeeper.quorum", "client-zk");
    configuration.set("hbase.client.zookeeper.property.clientPort", "2183");

    assertThat(HbaseServerTarget.from(configuration)).isEqualTo("server-zk:2182:/hbase");
    assertThat(HbaseServerTarget.from(configuration, true, false, false))
        .isEqualTo("client-zk:2183:/hbase");
  }

  @Test
  void clientZooKeeperPortFallsBackToServerPort() {
    Configuration configuration = new Configuration(false);
    configuration.set("hbase.zookeeper.property.clientPort", "2182");
    configuration.set("hbase.client.zookeeper.quorum", "client-zk");

    assertThat(HbaseServerTarget.from(configuration, true, false, false))
        .isEqualTo("client-zk:2182:/hbase");
  }

  @Test
  void rendersCanonicalMasterRegistryEndpoints() {
    Configuration configuration = new Configuration(false);
    configuration.set(REGISTRY_KEY, MASTER_REGISTRY);
    configuration.set("hbase.masters", "master-b:16001,master-a,master-b:16001,master-c:16002");
    configuration.set("hbase.master.port", "17000");

    assertThat(HbaseServerTarget.from(configuration, false, true, true))
        .isEqualTo("master-a:17000,master-b:16001,master-c:16002");
    assertThat(HbaseServerTarget.from(configuration, false, true, false))
        .isEqualTo("master-a:16000,master-b:16001,master-c:16002");
  }

  @Test
  void usesDefaultMasterPortWhenConfiguredPortIsZero() {
    Configuration configuration = new Configuration(false);
    configuration.set(REGISTRY_KEY, MASTER_REGISTRY);
    configuration.set("hbase.masters", "master-a");
    configuration.set("hbase.master.port", "0");

    assertThat(HbaseServerTarget.from(configuration, false, true, true))
        .isEqualTo("master-a:16000");
  }

  @Test
  void rendersMasterRegistryIpv6Endpoints() {
    Configuration configuration = new Configuration(false);
    configuration.set(REGISTRY_KEY, MASTER_REGISTRY);
    configuration.set("hbase.masters", "2001:db8::2,[2001:db8::1]:16001");

    assertThat(HbaseServerTarget.from(configuration, false, true, true))
        .isEqualTo("[2001:db8::1]:16001,[2001:db8::2]:16000");
  }

  @Test
  void ignoresRegistryConfigurationWhenUnsupported() {
    Configuration configuration = new Configuration(false);
    configuration.set(REGISTRY_KEY, MASTER_REGISTRY);
    configuration.set("hbase.zookeeper.quorum", "zk");
    configuration.set("hbase.masters", "master-a");

    assertThat(HbaseServerTarget.from(configuration, false, false, false))
        .isEqualTo("zk:2181:/hbase");
  }

  @Test
  void rejectsUnknownRegistry() {
    Configuration configuration = new Configuration(false);
    configuration.set(REGISTRY_KEY, "com.example.CustomRegistry");
    configuration.set("hbase.zookeeper.quorum", "zk");

    assertThat(HbaseServerTarget.from(configuration, false, true, false)).isNull();
  }

  @Test
  void rejectsIncompleteOrInvalidConfiguration() {
    Configuration masterConfiguration = new Configuration(false);
    masterConfiguration.set(REGISTRY_KEY, MASTER_REGISTRY);
    assertThat(HbaseServerTarget.from(masterConfiguration, false, true, true)).isNull();

    Configuration zkConfiguration = new Configuration(false);
    zkConfiguration.set("hbase.zookeeper.quorum", "zk-a,,zk-b");
    assertThat(HbaseServerTarget.from(zkConfiguration)).isNull();

    zkConfiguration.set("hbase.zookeeper.quorum", "zk-a,zk-b");
    zkConfiguration.set("hbase.zookeeper.property.clientPort", "not-a-port");
    assertThat(HbaseServerTarget.from(zkConfiguration)).isNull();

    zkConfiguration.set("hbase.zookeeper.property.clientPort", "2181");
    zkConfiguration.set("hbase.zookeeper.quorum", "user:password@zk-a/path");
    assertThat(HbaseServerTarget.from(zkConfiguration)).isNull();

    masterConfiguration.set("hbase.masters", "master-a");
    masterConfiguration.set("hbase.master.port", "not-a-port");
    assertThat(HbaseServerTarget.from(masterConfiguration, false, true, true)).isNull();
  }
}
