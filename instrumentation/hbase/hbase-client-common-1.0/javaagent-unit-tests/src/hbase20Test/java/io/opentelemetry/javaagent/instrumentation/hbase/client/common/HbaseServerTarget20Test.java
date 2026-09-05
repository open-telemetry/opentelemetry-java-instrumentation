/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hadoop.conf.Configuration;
import org.junit.jupiter.api.Test;

class HbaseServerTarget20Test {

  @Test
  void recognizesPrivateRegistryConfiguration() {
    Configuration configuration = new Configuration(false);
    configuration.set("hbase.client.registry.impl", "com.example.CustomRegistry");
    configuration.set("hbase.zookeeper.quorum", "zk");

    assertThat(HbaseServerTarget.from(configuration)).isNull();
  }

  @Test
  void selectsEarlierZooKeeperRegistry() {
    Configuration configuration = new Configuration(false);
    configuration.set(
        "hbase.client.registry.impl", "org.apache.hadoop.hbase.client.ZKAsyncRegistry");
    configuration.set("hbase.zookeeper.quorum", "zk");

    assertThat(HbaseServerTarget.from(configuration)).isEqualTo("zk:2181:/hbase");
  }
}
