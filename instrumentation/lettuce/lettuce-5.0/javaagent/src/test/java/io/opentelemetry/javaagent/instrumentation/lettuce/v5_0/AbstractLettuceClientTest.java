/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractLettuceClientTest {

  protected static final Logger logger = LoggerFactory.getLogger(AbstractLettuceClientTest.class);

  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  protected static final int DB_INDEX = 0;

  // Disable autoreconnect so we do not get stray traces popping up on server shutdown
  protected static final ClientOptions CLIENT_OPTIONS =
      ClientOptions.builder().autoReconnect(false).build();

  static final DockerImageName CONTAINER_IMAGE = DockerImageName.parse("redis:6.2.3-alpine");

  protected final GenericContainer<?> redisServer =
      new GenericContainer<>(CONTAINER_IMAGE)
          .withExposedPorts(6379)
          .withLogConsumer(new Slf4jLogConsumer(logger))
          .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));

  protected RedisClient redisClient;

  protected StatefulRedisConnection<String, String> connection;

  protected String ip;

  protected String host;

  protected int port;

  protected int secondaryPort;

  protected String embeddedDbUri;

  protected static boolean connectionTelemetryEnabled() {
    return Boolean.getBoolean("otel.instrumentation.lettuce.connection-telemetry.enabled");
  }

  protected String commandSpanName(String operationName) {
    return commandSpanName(operationName, port);
  }

  protected String commandSpanName(String operationName, int serverPort) {
    return emitStableDatabaseSemconv()
        ? operationName + " " + host + ":" + serverPort
        : operationName;
  }

  protected List<AttributeAssertion> commandAttributes(
      String operationName, String statement, AttributeAssertion... additionalAssertions) {
    return commandAttributes(operationName, statement, port, additionalAssertions);
  }

  @SuppressWarnings("OtelDeprecatedApiUsage") // using deprecated semconv
  protected List<AttributeAssertion> commandAttributes(
      String operationName,
      String statement,
      int serverPort,
      AttributeAssertion... additionalAssertions) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            asList(
                equalTo(SERVER_ADDRESS, host),
                equalTo(SERVER_PORT, serverPort),
                equalTo(maybeStable(DB_SYSTEM), REDIS),
                equalTo(maybeStable(DB_STATEMENT), statement),
                equalTo(maybeStable(DB_OPERATION), operationName)));
    Collections.addAll(assertions, additionalAssertions);
    return assertions;
  }

  protected StatefulRedisConnection<String, String> newContainerConnection() {
    GenericContainer<?> server =
        new GenericContainer<>(CONTAINER_IMAGE)
            .withExposedPorts(6379)
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));
    server.start();
    cleanup.deferCleanup(server::stop);

    secondaryPort = server.getMappedPort(6379);

    RedisClient client =
        RedisClient.create("redis://" + host + ":" + secondaryPort + "/" + DB_INDEX);
    client.setOptions(CLIENT_OPTIONS);
    cleanup.deferCleanup(client::shutdown);

    StatefulRedisConnection<String, String> statefulConnection = client.connect();
    cleanup.deferCleanup(statefulConnection);

    if (connectionTelemetryEnabled()) {
      testing.waitForTraces(1);
    }
    testing.clearData();

    return statefulConnection;
  }

  static void shutdown(RedisClient redisClient) {
    // using shutdownAsync instead of redisClient.shutdown() because there is a bug in the redis
    // client that can cause the shutdown to hang
    try {
      redisClient.shutdownAsync(0, 15, SECONDS).get(15, SECONDS);
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    } catch (Exception ignored) {
      // ignore
    }
  }
}
