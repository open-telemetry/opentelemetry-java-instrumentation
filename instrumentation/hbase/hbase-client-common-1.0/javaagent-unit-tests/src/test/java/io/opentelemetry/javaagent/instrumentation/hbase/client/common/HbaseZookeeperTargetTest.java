/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.zookeeper.ZKConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HbaseZookeeperTargetTest {

  @Test
  void rendersDefaults() {
    assertThat(HbaseZookeeperTarget.from(new Configuration(false), false, false))
        .isEqualTo("localhost:2181:/hbase");
  }

  @Test
  void sortsAndPreservesDuplicateExternalZooCfgEndpoints() {
    Configuration configuration = new Configuration(false);
    configuration.setBoolean("hbase.config.read.zookeeper.config", true);
    configuration.set("test.zk.client.port", "3218");
    configuration.set("hbase.zookeeper.quorum", "inactive-zk");
    configuration.set("hbase.zookeeper.property.clientPort", "2182");
    configuration.set("zookeeper.znode.parent", "/external");

    assertThat(HbaseZookeeperTarget.from(configuration, false, true))
        .isEqualTo("external-zk-a,external-zk-b,external-zk-b:3218:/external");
    assertThat(ZKConfig.getZKQuorumServersString(configuration).split(",", -1))
        .containsExactlyInAnyOrder(
            "external-zk-a:3218", "external-zk-b:3218", "external-zk-b:3218");
  }

  @Test
  void omitsTargetWhenExternalZooCfgClientPortIsUnavailable() {
    Configuration configuration = new Configuration(false);
    configuration.setBoolean("hbase.config.read.zookeeper.config", true);
    configuration.set("test.zk.client.port", "");
    configuration.set("hbase.zookeeper.quorum", "inactive-zk");
    configuration.set("hbase.zookeeper.property.clientPort", "2182");

    assertThat(HbaseZookeeperTarget.from(configuration, false, true)).isNull();
  }

  @Test
  void ignoresZooCfgFlagWhenUnsupported() {
    Configuration configuration = new Configuration(false);
    configuration.setBoolean("hbase.config.read.zookeeper.config", true);
    configuration.set("hbase.zookeeper.quorum", "active-zk");

    assertThat(HbaseZookeeperTarget.from(configuration, false, false))
        .isEqualTo("active-zk:2181:/hbase");
  }

  @Test
  void rendersCanonicalClusterKey() {
    Configuration configuration = new Configuration(false);
    configuration.set("hbase.zookeeper.quorum", "zk-b:2222,\nzk-a,zk-b:2222");
    configuration.set("hbase.zookeeper.property.clientPort", "3218");
    configuration.set("zookeeper.znode.parent", "/production");

    assertThat(HbaseZookeeperTarget.from(configuration, false, false))
        .isEqualTo("zk-b:2222,zk-a,zk-b:2222:3218:/production");
  }

  @Test
  void preservesQuorumsLargerThanFive() {
    Configuration configuration = new Configuration(false);
    configuration.set("hbase.zookeeper.quorum", "zk-g,zk-c,zk-a,zk-b,zk-c,zk-e,zk-d");

    assertThat(HbaseZookeeperTarget.from(configuration, false, false))
        .isEqualTo("zk-g,zk-c,zk-a,zk-b,zk-c,zk-e,zk-d:2181:/hbase");
  }

  @Test
  void preservesIpv6OrderAndDuplicates() {
    Configuration configuration = new Configuration(false);
    configuration.set("hbase.zookeeper.quorum", "2001:db8::2,[2001:db8::1],2001:db8::2");

    assertThat(HbaseZookeeperTarget.from(configuration, false, false))
        .isEqualTo("[2001:db8::2],[2001:db8::1],[2001:db8::2]:2181:/hbase");
  }

  @Test
  void prefersClientConfigurationWhenSupported() {
    Configuration configuration = new Configuration(false);
    configuration.set("hbase.zookeeper.quorum", "server-zk");
    configuration.set("hbase.zookeeper.property.clientPort", "2182");
    configuration.set("hbase.client.zookeeper.quorum", "client-zk");
    configuration.set("hbase.client.zookeeper.property.clientPort", "2183");

    assertThat(HbaseZookeeperTarget.from(configuration, false, false))
        .isEqualTo("server-zk:2182:/hbase");
    assertThat(HbaseZookeeperTarget.from(configuration, true, false))
        .isEqualTo("client-zk:2183:/hbase");
  }

  @Test
  void clientPortFallsBackToServerPort() {
    Configuration configuration = new Configuration(false);
    configuration.set("hbase.zookeeper.property.clientPort", "2182");
    configuration.set("hbase.client.zookeeper.quorum", "client-zk");

    assertThat(HbaseZookeeperTarget.from(configuration, true, false))
        .isEqualTo("client-zk:2182:/hbase");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"zk-a,,zk-b", "user:password@zk-a/path", " zk-a,zk-b", "not:an:ipv6-address"})
  void rejectsInvalidOrUnsafeQuorum(String quorum) {
    Configuration configuration = new Configuration(false);
    configuration.set("hbase.zookeeper.quorum", quorum);

    assertThat(HbaseZookeeperTarget.from(configuration, false, false)).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "0", "65536", "not-a-port"})
  void rejectsInvalidClientPort(String clientPort) {
    Configuration configuration = new Configuration(false);
    configuration.set("hbase.zookeeper.property.clientPort", clientPort);

    assertThat(HbaseZookeeperTarget.from(configuration, false, false)).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"/", "relative", "/trailing/"})
  void rejectsInvalidZnodeParent(String znodeParent) {
    Configuration configuration = new Configuration(false);
    configuration.set("zookeeper.znode.parent", znodeParent);

    assertThat(HbaseZookeeperTarget.from(configuration, false, false)).isNull();
  }
}
