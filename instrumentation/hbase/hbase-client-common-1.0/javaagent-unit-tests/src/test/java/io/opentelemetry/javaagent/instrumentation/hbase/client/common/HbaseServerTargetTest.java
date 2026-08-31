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
  void sortsAndPreservesDuplicateExternalZooCfgEndpoints() {
    Configuration configuration = new Configuration(false);
    configuration.setBoolean("hbase.config.read.zookeeper.config", true);
    configuration.set("test.zk.client.port", "3218");
    configuration.set("hbase.zookeeper.quorum", "inactive-zk");
    configuration.set("hbase.zookeeper.property.clientPort", "2182");
    configuration.set("zookeeper.znode.parent", "/external");

    assertThat(HbaseServerTarget.from(configuration))
        .isEqualTo("external-zk-a,external-zk-b,external-zk-b:3218:/external");
  }

  @Test
  void omitsTargetWhenExternalZooCfgClientPortIsUnavailable() {
    Configuration configuration = new Configuration(false);
    configuration.setBoolean("hbase.config.read.zookeeper.config", true);
    configuration.set("test.zk.client.port", "");
    configuration.set("hbase.zookeeper.quorum", "inactive-zk");
    configuration.set("hbase.zookeeper.property.clientPort", "2182");

    assertThat(HbaseServerTarget.from(configuration)).isNull();
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
    configuration.set("hbase.zookeeper.quorum", "zk-b:2222,\nzk-a,zk-b:2222");
    configuration.set("hbase.zookeeper.property.clientPort", "3218");
    configuration.set("zookeeper.znode.parent", "/production");

    assertThat(HbaseServerTarget.from(configuration, false, true, false))
        .isEqualTo("zk-b:2222,zk-a,zk-b:2222:3218:/production");
  }

  @Test
  void preservesZooKeeperIpv6OrderAndDuplicates() {
    Configuration configuration = new Configuration(false);
    configuration.set("hbase.zookeeper.quorum", "2001:db8::2,[2001:db8::1],2001:db8::2");

    assertThat(HbaseServerTarget.from(configuration))
        .isEqualTo("[2001:db8::2],[2001:db8::1],[2001:db8::2]:2181:/hbase");
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
  void rendersMasterRegistryEndpointsIndependentOfSourceOrder() {
    Configuration firstConfiguration = new Configuration(false);
    firstConfiguration.set(REGISTRY_KEY, MASTER_REGISTRY);
    firstConfiguration.set("hbase.masters", "master-b:16001,master-a,master-b:16001,master-c");
    firstConfiguration.set("hbase.master.port", "17000");

    Configuration secondConfiguration = new Configuration(false);
    secondConfiguration.set(REGISTRY_KEY, MASTER_REGISTRY);
    secondConfiguration.set("hbase.masters", "master-c,master-b:16001,master-a,master-b:16001");
    secondConfiguration.set("hbase.master.port", "17000");

    assertThat(HbaseServerTarget.from(firstConfiguration, false, true, true))
        .isEqualTo("master-a:17000,master-b:16001,master-b:16001,master-c:17000");
    assertThat(HbaseServerTarget.from(secondConfiguration, false, true, true))
        .isEqualTo("master-a:17000,master-b:16001,master-b:16001,master-c:17000");
    assertThat(HbaseServerTarget.from(firstConfiguration, false, true, false))
        .isEqualTo("master-a:16000,master-b:16001,master-b:16001,master-c:16000");
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
  void rendersMasterRegistryDefaultAddress() {
    Configuration configuration = new Configuration(false);
    configuration.set(REGISTRY_KEY, MASTER_REGISTRY);
    configuration.set("hbase.master.hostname", "master.test");
    configuration.setInt("hbase.master.port", 17000);

    assertThat(HbaseServerTarget.from(configuration, false, true, true))
        .isEqualTo("master.test:17000");

    configuration.set("hbase.masters", "");
    assertThat(HbaseServerTarget.from(configuration, false, true, true))
        .isEqualTo("master.test:17000");
  }

  @Test
  void rendersMasterRegistryIpv6Endpoints() {
    Configuration configuration = new Configuration(false);
    configuration.set(REGISTRY_KEY, MASTER_REGISTRY);
    configuration.set("hbase.masters", "2001:db8::2,[2001:db8::1]:16001,[2001:db8::3]");

    assertThat(HbaseServerTarget.from(configuration, false, true, true))
        .isEqualTo("[2001:db8::1]:16001,[2001:db8::2]:16000,[2001:db8::3]:16000");
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

    zkConfiguration.set("hbase.zookeeper.quorum", " zk-a,zk-b");
    assertThat(HbaseServerTarget.from(zkConfiguration)).isNull();

    zkConfiguration.set("hbase.zookeeper.quorum", "not:an:ipv6-address");
    assertThat(HbaseServerTarget.from(zkConfiguration)).isNull();

    masterConfiguration.set("hbase.masters", "master-a");
    masterConfiguration.set("hbase.master.port", "not-a-port");
    assertThat(HbaseServerTarget.from(masterConfiguration, false, true, true)).isNull();
  }
}
