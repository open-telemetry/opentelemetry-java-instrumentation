/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Arrays.asList;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractLettuceClientTest {
  private static final Logger logger = LoggerFactory.getLogger(AbstractLettuceClientTest.class);

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  protected static final int DB_INDEX = 0;

  protected GenericContainer<?> redisServer =
      new GenericContainer<>("redis:6.2.3-alpine")
          .withExposedPorts(6379)
          .withLogConsumer(new Slf4jLogConsumer(logger))
          .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));

  protected RedisClient redisClient;
  protected StatefulRedisConnection<String, String> connection;
  protected String host;
  protected String ip;
  protected int port;
  protected String embeddedDbUri;

  protected abstract RedisClient createClient(String uri);

  protected abstract InstrumentationExtension testing();

  protected ContainerConnection newContainerConnection() {
    GenericContainer<?> server =
        new GenericContainer<>("redis:6.2.3-alpine")
            .withExposedPorts(6379)
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));
    server.start();
    cleanup.deferCleanup(server::stop);

    long serverPort = server.getMappedPort(6379);

    RedisClient client = createClient("redis://" + host + ":" + serverPort + "/" + DB_INDEX);
    client.setOptions(LettuceTestUtil.CLIENT_OPTIONS);
    cleanup.deferCleanup(client::shutdown);

    StatefulRedisConnection<String, String> statefulConnection = client.connect();
    cleanup.deferCleanup(statefulConnection);

    return new ContainerConnection(statefulConnection, serverPort);
  }

  protected static class ContainerConnection {
    public final StatefulRedisConnection<String, String> connection;
    public final long port;

    private ContainerConnection(StatefulRedisConnection<String, String> connection, long port) {
      this.connection = connection;
      this.port = port;
    }
  }

  protected static List<AttributeAssertion> addExtraAttributes(AttributeAssertion... assertions) {
    return asList(assertions);
  }

  protected static void assertCommandEncodeEvents(SpanData span) {
    assertThat(span)
        .hasEventsSatisfyingExactly(
            event -> event.hasName("redis.encode.start"),
            event -> event.hasName("redis.encode.end"));
  }

  protected String spanName(String operation) {
    return spanName(operation, host, port);
  }

  protected static String spanName(String operation, String serverAddress, long serverPort) {
    if (emitStableDatabaseSemconv()) {
      return operation + " " + serverAddress + ":" + serverPort;
    }
    return operation;
  }
}
