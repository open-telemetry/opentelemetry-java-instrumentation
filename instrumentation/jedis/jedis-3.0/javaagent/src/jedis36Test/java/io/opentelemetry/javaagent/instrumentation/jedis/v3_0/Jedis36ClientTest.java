/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

class Jedis36ClientTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static GenericContainer<?> redisServer;
  private static int port;

  @BeforeAll
  static void setup() {
    port = PortUtils.findOpenPort();
    redisServer = new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);
    redisServer.setPortBindings(singletonList(port + ":6379"));
    redisServer.start();
    cleanup.deferAfterAll(redisServer::stop);
  }

  @Test
  void pooledHostAndPortCommandUsesOriginalTarget() {
    HostAndPort endpoint = HostAndPort.parseString("localhost:" + port);
    JedisPool pool =
        new JedisPool(
            new GenericObjectPoolConfig<Jedis>(),
            endpoint,
            DefaultJedisClientConfig.builder().build());
    cleanup.deferCleanup(pool);

    try (Jedis pooled = pool.getResource()) {
      pooled.set("pooled-host-and-port", "value");
    }

    testing.waitForTraces(1);
    assertThat(testing.spans())
        .singleElement()
        .satisfies(
            span -> {
              assertThat(span.getAttributes().get(SERVER_ADDRESS))
                  .isEqualTo(emitStableDatabaseSemconv() ? "localhost" : endpoint.getHost());
              assertThat(span.getAttributes().get(SERVER_PORT)).isEqualTo((long) port);
            });
  }
}
