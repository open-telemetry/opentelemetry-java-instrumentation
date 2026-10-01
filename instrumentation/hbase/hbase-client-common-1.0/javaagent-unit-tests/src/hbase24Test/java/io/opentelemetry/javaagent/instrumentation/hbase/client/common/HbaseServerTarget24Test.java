/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.UnknownHostException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.client.MasterRegistry;
import org.junit.jupiter.api.Test;

class HbaseServerTarget24Test {

  private static final String REGISTRY_KEY = "hbase.client.registry.impl";
  private static final String MASTER_REGISTRY = "org.apache.hadoop.hbase.client.MasterRegistry";

  @Test
  void selectsConfiguredMasterRegistry() {
    Configuration configuration = new Configuration(false);
    configuration.set(REGISTRY_KEY, MASTER_REGISTRY);
    configuration.set("hbase.masters", "master-b,master-a");
    configuration.set("hbase.master.port", "17000");

    assertThat(HbaseServerTarget.from(configuration)).isEqualTo("master-a:17000,master-b:17000");
  }

  @Test
  void selectsImplicitMasterRegistryTarget() throws UnknownHostException {
    Configuration configuration = new Configuration(false);
    configuration.set(REGISTRY_KEY, MASTER_REGISTRY);
    configuration.setInt("hbase.master.port", 17000);

    assertThat(HbaseServerTarget.from(configuration))
        .isEqualTo(MasterRegistry.getMasterAddr(configuration));
  }
}
