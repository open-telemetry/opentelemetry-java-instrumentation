/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import com.google.common.collect.ImmutableMap;
import com.lambdaworks.redis.ClientOptions;
import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.api.StatefulRedisConnection;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

abstract class AbstractLettuceClientTest {

  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  static final DockerImageName containerImage = DockerImageName.parse("redis:6.2.3-alpine");

  protected static final int DB_INDEX = 0;

  // Disable auto reconnect, so we do not get stray traces popping up on server shutdown
  protected static final ClientOptions CLIENT_OPTIONS =
      new ClientOptions.Builder().autoReconnect(false).build();

  protected static final GenericContainer<?> redisServer =
      new GenericContainer<>(containerImage).withExposedPorts(6379);

  protected static String host;
  protected static int port;
  protected static int incorrectPort;
  protected static String dbUriNonExistent;
  protected static String embeddedDbUri;

  protected static final ImmutableMap<String, String> testHashMap =
      ImmutableMap.of(
          "firstname", "John",
          "lastname", "Doe",
          "age", "53");

  protected static RedisClient redisClient;
  protected static StatefulRedisConnection<String, String> connection;

  protected StatefulRedisConnection<String, String> newContainerConnection() {
    GenericContainer<?> server = new GenericContainer<>(containerImage).withExposedPorts(6379);
    server.start();
    cleanup.deferCleanup(server::stop);

    long shutdownServerPort = server.getMappedPort(6379);

    RedisClient client =
        RedisClient.create("redis://" + host + ":" + shutdownServerPort + "/" + DB_INDEX);
    client.setOptions(CLIENT_OPTIONS);
    StatefulRedisConnection<String, String> connection = client.connect();
    cleanup.deferCleanup(connection);
    cleanup.deferCleanup(client::shutdown);

    // 1 connect trace
    testing.waitForTraces(1);
    testing.clearData();

    return connection;
  }
}
