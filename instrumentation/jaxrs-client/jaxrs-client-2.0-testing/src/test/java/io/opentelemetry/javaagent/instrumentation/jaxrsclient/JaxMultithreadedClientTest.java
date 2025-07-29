/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.testing.internal.armeria.common.HttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpStatus;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import io.opentelemetry.testing.internal.armeria.server.ServerBuilder;
import io.opentelemetry.testing.internal.armeria.testing.junit5.server.ServerExtension;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.client.Client;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.RegisterExtension;

class JaxMultithreadedClientTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  static ServerExtension server =
      new ServerExtension() {
        @Override
        protected void configure(ServerBuilder sb) {
          sb.service(
              "/success",
              (ctx, req) -> HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT, "Hello."));
        }
      };

  @BeforeAll
  static void setUp() {
    server.start();
  }

  @AfterAll
  static void cleanUp() {
    server.stop();
  }

  @SuppressWarnings("CatchingUnchecked")
  boolean checkUri(JerseyClientBuilder builder, URI uri) {
    try {
      Client client = builder.build();
      client.target(uri).request().get();
    } catch (Exception e) {
      return true;
    }
    return false;
  }

  @DisplayName("multiple threads using the same builder works")
  void testMultipleThreads() throws InterruptedException {
    URI uri = server.httpUri().resolve("/success");
    JerseyClientBuilder builder = new JerseyClientBuilder();

    // Start 10 threads and do 50 requests each
    CountDownLatch latch = new CountDownLatch(10);
    for (int i = 0; i < 10; i++) {
      new Thread(
              new Runnable() {
                @Override
                public void run() {
                  boolean hadErrors = false;
                  for (int j = 0; j < 50; j++) {
                    hadErrors = hadErrors || checkUri(builder, uri);
                  }
                  assertThat(hadErrors).isFalse();
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10, TimeUnit.SECONDS);
  }
}
