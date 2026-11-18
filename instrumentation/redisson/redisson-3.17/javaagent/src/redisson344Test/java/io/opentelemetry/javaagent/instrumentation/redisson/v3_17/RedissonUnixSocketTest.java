/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.v3_17;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
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

      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasKind(CLIENT)
                          .hasAttributesSatisfying(
                              attributes ->
                                  assertThat(attributes.asMap())
                                      .containsEntry(NETWORK_PEER_ADDRESS, socketPath))));
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
