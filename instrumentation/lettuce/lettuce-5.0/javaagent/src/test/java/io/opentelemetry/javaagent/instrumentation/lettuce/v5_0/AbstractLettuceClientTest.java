/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

abstract class AbstractLettuceClientTest {

  protected static final Logger logger = LoggerFactory.getLogger(AbstractLettuceClientTest.class);

  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  protected static final int DB_INDEX = 0;

  // Disable autoreconnect so we do not get stray traces popping up on server shutdown
  protected static final ClientOptions CLIENT_OPTIONS =
      ClientOptions.builder().autoReconnect(false).build();

  static final DockerImageName containerImage = DockerImageName.parse("redis:6.2.3-alpine");

  protected static final GenericContainer<?> redisServer =
      new GenericContainer<>(containerImage)
          .withExposedPorts(6379)
          .withLogConsumer(new Slf4jLogConsumer(logger))
          .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));

  protected static RedisClient redisClient;

  protected static StatefulRedisConnection<String, String> connection;

  protected static String ip;

  protected static String host;

  protected static int port;

  protected static String embeddedDbUri;

  protected static StatefulRedisConnection<String, String> newContainerConnection() {
    GenericContainer<?> server =
        new GenericContainer<>(containerImage)
            .withExposedPorts(6379)
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));
    server.start();
    cleanup.deferCleanup(server::stop);

    long serverPort = server.getMappedPort(6379);

    RedisClient client = RedisClient.create("redis://" + host + ":" + serverPort + "/" + DB_INDEX);
    client.setOptions(CLIENT_OPTIONS);
    cleanup.deferCleanup(client::shutdown);

    StatefulRedisConnection<String, String> statefulConnection = client.connect();
    cleanup.deferCleanup(statefulConnection);

    // 1 connect trace
    testing.waitForTraces(1);
    testing.clearData();

    return statefulConnection;
  }

  static void shutdown(RedisClient redisClient) {
    // using shutdownAsync instead of redisClient.shutdown() because there is a bug in the redis
    // client that can cause the shutdown to hang
    try {
      redisClient.shutdownAsync(0, 15, TimeUnit.SECONDS).get(15, TimeUnit.SECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    } catch (Exception exception) {
      // ignore
    }
  }
}
