/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nats.v2_17;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.opentelemetry.instrumentation.nats.v2_17.AbstractNatsInstrumentationDispatcherTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

class NatsInstrumentationDispatcherTest extends AbstractNatsInstrumentationDispatcherTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  static final DockerImageName natsImage;
  static final GenericContainer<?> natsContainer;
  static final Connection natsConnection;

  static {
    natsImage = DockerImageName.parse("nats:2.11.2-alpine3.21");

    natsContainer = new GenericContainer<>(natsImage).withExposedPorts(4222);
    natsContainer.start();

    try {
      String host = natsContainer.getHost();
      Integer port = natsContainer.getMappedPort(4222);
      natsConnection = Nats.connect("nats://" + host + ":" + port);
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @AfterAll
  static void afterAll() throws InterruptedException, TimeoutException {
    natsConnection.drain(Duration.ZERO);
    natsContainer.close();
  }

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected Connection connection() {
    return natsConnection;
  }
}
