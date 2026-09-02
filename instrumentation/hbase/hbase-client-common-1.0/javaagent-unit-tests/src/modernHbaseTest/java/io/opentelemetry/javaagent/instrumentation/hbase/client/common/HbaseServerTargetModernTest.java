/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hadoop.conf.Configuration;
import org.junit.jupiter.api.Test;

class HbaseServerTargetModernTest {

  private static final String REGISTRY_KEY = "hbase.client.registry.impl";
  private static final String MASTER_REGISTRY = "org.apache.hadoop.hbase.client.MasterRegistry";

  @Test
  void omitsUnconfiguredMasterRegistryTarget() throws ClassNotFoundException {
    assertThat(Class.forName(MASTER_REGISTRY)).isNotNull();

    Configuration configuration = new Configuration(false);
    configuration.set(REGISTRY_KEY, MASTER_REGISTRY);

    assertThat(HbaseServerTarget.from(configuration)).isNull();
  }
}
