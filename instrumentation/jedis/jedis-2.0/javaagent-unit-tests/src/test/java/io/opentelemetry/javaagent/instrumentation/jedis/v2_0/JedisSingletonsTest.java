/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import redis.clients.jedis.Connection;

class JedisSingletonsTest {

  @Test
  void invalidUpdatesClearCapturedConnectionTarget() {
    Connection connection = new Connection("localhost", 6380);

    JedisSingletons.captureConnectionTarget(connection);
    assertThat(JedisSingletons.connectionTarget(connection)).isNotNull();

    connection.setHost(null);
    JedisSingletons.captureConnectionTarget(connection);
    assertThat(JedisSingletons.connectionTarget(connection)).isNull();

    connection.setHost("localhost");
    JedisSingletons.captureConnectionTarget(connection);
    assertThat(JedisSingletons.connectionTarget(connection)).isNotNull();

    connection.setPort(65536);
    JedisSingletons.captureConnectionTarget(connection);
    assertThat(JedisSingletons.connectionTarget(connection)).isNull();
  }
}
