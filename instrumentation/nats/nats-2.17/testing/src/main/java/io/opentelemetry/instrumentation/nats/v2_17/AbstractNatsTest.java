/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractNatsTest {

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private GenericContainer<?> natsContainer;
  Connection connection;

  protected abstract InstrumentationExtension testing();

  @BeforeAll
  void beforeAll() throws IOException, InterruptedException {
    DockerImageName natsImage = DockerImageName.parse("nats:2.11.2-alpine3.21");

    natsContainer = new GenericContainer<>(natsImage).withExposedPorts(4222);
    cleanup.deferAfterAll(natsContainer);
    natsContainer.start();

    String host = natsContainer.getHost();
    int port = natsContainer.getMappedPort(4222);
    connection = Nats.connect("nats://" + host + ":" + port);
    cleanup.deferAfterAll(() -> connection.drain(Duration.ZERO));
  }
}
