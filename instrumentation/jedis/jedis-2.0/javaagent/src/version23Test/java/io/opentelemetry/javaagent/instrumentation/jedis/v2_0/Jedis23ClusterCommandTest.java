/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisClusterCommand;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisSlotBasedConnectionHandler;
import redis.clients.util.JedisClusterCRC16;

class Jedis23ClusterCommandTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static GenericContainer<?> redis;

  @BeforeAll
  static void setup() throws IOException, InterruptedException {
    int port = PortUtils.findOpenPort();
    redis = new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);
    redis.setPortBindings(singletonList(port + ":6379"));
    redis.withCommand(
        "redis-server",
        "--cluster-enabled",
        "yes",
        "--cluster-config-file",
        "/tmp/nodes.conf",
        "--cluster-announce-ip",
        "127.0.0.1",
        "--cluster-announce-port",
        Integer.toString(port));
    redis.start();
    cleanup.deferAfterAll(redis::stop);
    Container.ExecResult result =
        redis.execInContainer("sh", "-c", "redis-cli cluster addslots $(seq 0 16383) >/dev/null");
    assertThat(result.getExitCode()).isZero();
    Jedis node = new Jedis(redis.getHost(), port);
    cleanup.deferAfterAll(node::disconnect);
    await().untilAsserted(() -> assertThat(node.clusterInfo()).contains("cluster_state:ok"));
  }

  @Test
  void zeroArgumentExecuteIsInstrumented() {
    String response = new TestClusterCommand(redis.getHost(), redis.getMappedPort(6379)).run();

    assertThat(response).isEqualTo("PONG");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("PING " + redis.getHost() + ":" + redis.getMappedPort(6379))));
  }

  @Test
  void failedClusterCommandUsesServerReplyAndDoesNotLeak() {
    JedisCluster cluster = new JedisCluster(emptySet());
    seedNode(cluster.getClusterNodes());
    cluster.lpush("wrong-type", "value");
    testing.clearData();

    assertThatThrownBy(() -> cluster.get("wrong-type")).hasMessageStartingWith("WRONGTYPE");
    assertErrorType("WRONGTYPE");

    testing.clearData();
    assertThat(cluster.set("after-error", "value")).isEqualTo("OK");
    assertErrorType(null);
  }

  private static void assertErrorType(String errorType) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.satisfies(
                        spanData ->
                            assertThat(spanData.getAttributes().get(ERROR_TYPE))
                                .isEqualTo(errorType))));
    testing.waitAndAssertMetrics(
        "io.opentelemetry.jedis-2.0",
        "db.client.operation.duration",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric.getHistogramData().getPoints())
                        .allSatisfy(
                            point ->
                                assertThat(point.getAttributes().get(ERROR_TYPE))
                                    .isEqualTo(errorType))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void redirectedCommandUsesTerminalReply(boolean fail) {
    JedisSlotBasedConnectionHandler handler = new JedisSlotBasedConnectionHandler(emptySet());
    seedNode(handler.getNodes());
    String key = fail ? "redirect-error" : "redirect-success";
    String script =
        "if redis.call('EXISTS', KEYS[1]) == 0 then "
            + "redis.call('SET', KEYS[1], 'attempted'); "
            + "return redis.error_reply('MOVED "
            + JedisClusterCRC16.getSlot(key)
            + " 127.0.0.1:"
            + redis.getMappedPort(6379)
            + "') end "
            + (fail
                ? "return redis.error_reply('WRONGTYPE terminal failure')"
                : "return 'recovered'");
    JedisClusterCommand<Object> command =
        new JedisClusterCommand<Object>(handler, 2000, 3) {
          @Override
          public Object execute() {
            return handler.getConnection().eval(script, 1, key);
          }
        };
    testing.clearData();

    if (fail) {
      assertThatThrownBy(() -> command.run(key)).hasMessageStartingWith("WRONGTYPE");
    } else {
      assertThat(command.run(key)).isEqualTo("recovered");
    }
    assertErrorType(fail ? "WRONGTYPE" : null);

    testing.clearData();
    assertThat(new TestClusterCommand(redis.getHost(), redis.getMappedPort(6379)).run())
        .isEqualTo("PONG");
    assertErrorType(null);
  }

  private static void seedNode(Map<String, JedisPool> nodes) {
    // Jedis 2.3 cannot parse the cluster bus ports in modern CLUSTER NODES replies.
    int port = redis.getMappedPort(6379);
    JedisPool pool = new JedisPool("127.0.0.1", port);
    cleanup.deferCleanup(pool::destroy);
    nodes.put("127.0.0.1" + port, pool);
  }

  static class TestClusterCommand extends JedisClusterCommand<String> {
    private final String host;
    private final int port;

    TestClusterCommand(String host, int port) {
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
