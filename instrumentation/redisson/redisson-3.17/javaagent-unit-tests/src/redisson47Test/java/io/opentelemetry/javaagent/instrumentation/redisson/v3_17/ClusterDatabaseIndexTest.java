/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.v3_17;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.redisson.config.Config;
import org.redisson.config.ConfigServerTargetUtil317;

class ClusterDatabaseIndexTest {

  @Test
  void configuredClusterDatabaseIndex() {
    Config config = new Config();
    config.useClusterServers().setDatabase(2);

    assertThat(ConfigServerTargetUtil317.databaseIndex(config)).isEqualTo(2);
  }
}
