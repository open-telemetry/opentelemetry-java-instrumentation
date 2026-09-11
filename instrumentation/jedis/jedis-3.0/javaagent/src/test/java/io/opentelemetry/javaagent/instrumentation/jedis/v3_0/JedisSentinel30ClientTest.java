/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisSentinelPool;

class JedisSentinel30ClientTest {

  private static final String MASTER_NAME = "mymaster";

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static String sentinelEndpoint;

  @BeforeAll
  static void setup() {
    int masterPort = PortUtils.findOpenPort();
    int sentinelPort = PortUtils.findOpenPort();
    String sentinelConfig =
        "port "
            + sentinelPort
            + "\\nsentinel monitor "
            + MASTER_NAME
            + " 127.0.0.1 "
            + masterPort
            + " 1\\n";
    GenericContainer<?> sentinelServer =
        new GenericContainer<>("redis:6.2.3-alpine")
            .withExposedPorts(masterPort, sentinelPort)
            .withCommand(
                "sh",
                "-c",
                "redis-server --port "
                    + masterPort
                    + " --daemonize yes && printf '"
                    + sentinelConfig
                    + "' > /tmp/sentinel.conf && exec redis-server /tmp/sentinel.conf --sentinel")
            .waitingFor(Wait.forListeningPorts(masterPort, sentinelPort));
    sentinelServer.setPortBindings(
        asList(masterPort + ":" + masterPort, sentinelPort + ":" + sentinelPort));
    sentinelServer.start();
    cleanup.deferAfterAll(sentinelServer::stop);
    sentinelEndpoint = sentinelServer.getHost() + ":" + sentinelPort;
  }

  @Test
  void discoveryAndCommandsUseConfiguredTarget() {
    JedisSentinelPool pool = new JedisSentinelPool(MASTER_NAME, singleton(sentinelEndpoint));
    Jedis jedis = pool.getResource();
    try {
      jedis.set("key", "value");
    } finally {
      jedis.close();
      pool.destroy();
    }

    await()
        .untilAsserted(
            () -> {
              assertThat(testing.spans())
                  .filteredOn(span -> span.getName().startsWith("SET"))
                  .anySatisfy(
                      span -> {
                        if (emitStableDatabaseSemconv()) {
                          assertThat(span.getAttributes().get(SERVER_ADDRESS))
                              .isEqualTo(sentinelEndpoint + "/" + MASTER_NAME);
                          assertThat(span.getAttributes().get(SERVER_PORT)).isNull();
                        } else {
                          assertThat(span.getAttributes().get(SERVER_ADDRESS))
                              .isNotEqualTo(sentinelEndpoint + "/" + MASTER_NAME);
                          assertThat(span.getAttributes().get(SERVER_PORT)).isNotNull();
                        }
                      });
              assertThat(testing.spans())
                  .filteredOn(span -> span.getName().startsWith("SENTINEL"))
                  .isNotEmpty()
                  .allSatisfy(
                      span -> {
                        if (emitStableDatabaseSemconv()) {
                          assertThat(span.getAttributes().get(SERVER_ADDRESS))
                              .isEqualTo(sentinelEndpoint + "/" + MASTER_NAME);
                          assertThat(span.getAttributes().get(SERVER_PORT)).isNull();
                        } else {
                          assertThat(span.getAttributes().get(SERVER_ADDRESS))
                              .isNotEqualTo(sentinelEndpoint + "/" + MASTER_NAME);
                          assertThat(span.getAttributes().get(SERVER_PORT)).isNotNull();
                        }
                      });
              assertThat(testing.spans())
                  .filteredOn(span -> span.getName().startsWith("SUBSCRIBE"))
                  .isNotEmpty()
                  .allSatisfy(
                      span -> {
                        if (emitStableDatabaseSemconv()) {
                          assertThat(span.getAttributes().get(SERVER_ADDRESS))
                              .isEqualTo(sentinelEndpoint + "/" + MASTER_NAME);
                          assertThat(span.getAttributes().get(SERVER_PORT)).isNull();
                        } else {
                          assertThat(span.getAttributes().get(SERVER_ADDRESS))
                              .isNotEqualTo(sentinelEndpoint + "/" + MASTER_NAME);
                          assertThat(span.getAttributes().get(SERVER_PORT)).isNotNull();
                        }
                      });
            });
  }
}
