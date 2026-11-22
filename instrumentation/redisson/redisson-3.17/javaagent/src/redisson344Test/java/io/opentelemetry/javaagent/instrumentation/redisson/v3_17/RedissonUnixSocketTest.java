/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.v3_17;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.TransportMode;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

@EnabledOnOs(OS.LINUX)
@EnabledIfSystemProperty(named = "os.arch", matches = "amd64|x86_64")
class RedissonUnixSocketTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void commandUsesTheUnixSocketPeer(@TempDir Path tempDir) throws IOException {
    Files.setPosixFilePermissions(tempDir, PosixFilePermissions.fromString("rwxrwxrwx"));

    String socketPath = tempDir.resolve("redis.sock").toString();
    GenericContainer<?> redis =
        new GenericContainer<>("redis:6.2.3-alpine")
            .withCommand(
                "redis-server",
                "--port",
                "0",
                "--unixsocket",
                "/redis/redis.sock",
                "--unixsocketperm",
                "777")
            .withFileSystemBind(tempDir.toString(), "/redis", BindMode.READ_WRITE)
            .waitingFor(Wait.forLogMessage(".*ready to accept connections.*\\n", 1));
    RedissonClient client = null;
    try {
      redis.start();

      Config config = new Config();
      config.setTransportMode(TransportMode.EPOLL);
      config.useSingleServer().setAddress("redis+uds://" + socketPath);
      client = Redisson.create(config);

      testing.clearData();
      client.getBucket("uds-test").set("ok");

      testing.waitAndAssertTraces(trace ->
          trace.hasSpansSatisfyingExactly(span ->
              span.hasKind(CLIENT)
                  .hasAttributesSatisfyingExactly(
                      equalTo(NETWORK_PEER_ADDRESS, socketPath),
                      equalTo(NETWORK_PEER_PORT, null),
                      equalTo(SERVER_ADDRESS, null),
                      equalTo(SERVER_PORT, null),
                      equalTo(DB_SYSTEM_NAME, REDIS),
                      equalTo(DB_NAMESPACE, "0"),
                      equalTo(DB_QUERY_TEXT, "SET uds-test ?"),
                      equalTo(DB_OPERATION_NAME, "SET"))));
    } finally {
      try {
        if (client != null) {
          client.shutdown();
        }
      } finally {
        redis.stop();
      }
    }
  }
}
