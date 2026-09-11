/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClusterCommand;

class Jedis23ClusterCommandTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static final GenericContainer<?> redis =
      new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);

  @BeforeAll
  static void setup() {
    redis.start();
    cleanup.deferAfterAll(redis::stop);
  }

  @Test
  void zeroArgumentExecuteIsInstrumented() {
    String response = new TestClusterCommand(redis.getHost(), redis.getMappedPort(6379)).run();

    assertThat(response).isEqualTo("PONG");
    testing.waitForTraces(1);
    assertThat(testing.spans())
        .singleElement()
        .satisfies(span -> assertThat(span.getName()).startsWith("PING"));
  }

  private static final class TestClusterCommand extends JedisClusterCommand<String> {
    private final String host;
    private final int port;

    private TestClusterCommand(String host, int port) {
      super(null, 0, 0);
      this.host = host;
      this.port = port;
    }

    public String run() {
      return execute();
    }

    @Override
    public String execute() {
      Jedis jedis = new Jedis(host, port);
      try {
        return jedis.ping();
      } finally {
        jedis.disconnect();
      }
    }
  }
}
