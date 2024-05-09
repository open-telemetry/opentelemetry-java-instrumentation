/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractLettuceClientTest {
  protected static final Logger logger = LoggerFactory.getLogger(AbstractLettuceClientTest.class);

  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  public InstrumentationExtension getInstrumentationExtension() {
    return testing;
  }

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  protected static final int DB_INDEX = 0;

  protected static GenericContainer<?> redisServer =
      new GenericContainer<>("redis:6.2.3-alpine")
          .withExposedPorts(6379)
          .withLogConsumer(new Slf4jLogConsumer(logger))
          .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));

  protected static RedisClient redisClient;

  protected static StatefulRedisConnection<String, String> connection;

  protected abstract RedisClient createClient(String uri);

  protected static String host;

  protected static String ip;

  protected static int port;

  protected static String embeddedDbUri;

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
}
