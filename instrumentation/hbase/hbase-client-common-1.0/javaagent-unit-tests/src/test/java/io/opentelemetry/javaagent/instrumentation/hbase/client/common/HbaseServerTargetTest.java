/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hadoop.conf.Configuration;
import org.junit.jupiter.api.Test;

class HbaseServerTargetTest {

  @Test
  void ignoresRegistryConfigurationOnHbase10() {
    Configuration configuration = new Configuration(false);
    configuration.set(
        "hbase.client.registry.impl", "org.apache.hadoop.hbase.client.MasterRegistry");
    configuration.set("hbase.zookeeper.quorum", "zk");
    configuration.set("hbase.masters", "master-a");

    assertThat(HbaseServerTarget.from(configuration)).isEqualTo("zk:2181:/hbase");
  }
}
