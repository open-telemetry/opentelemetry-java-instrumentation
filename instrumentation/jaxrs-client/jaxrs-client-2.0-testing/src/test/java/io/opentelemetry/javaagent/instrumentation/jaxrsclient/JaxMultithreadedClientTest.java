/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient;

import static java.util.concurrent.TimeUnit.SECONDS;
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
import java.util.concurrent.atomic.AtomicBoolean;
import javax.ws.rs.client.Client;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JaxMultithreadedClientTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  static final ServerExtension server =
      new ServerExtension() {
        @Override
        protected void configure(ServerBuilder sb) {
          sb.service(
              "/success",
              (ctx, req) -> HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT, "Hello."));
        }
      };

  @SuppressWarnings("CatchingUnchecked")
  boolean checkUri(JerseyClientBuilder builder, URI uri) {
    Client client = builder.build();
    try {
      client.target(uri).request().get().close();
      return false;
    } catch (Exception ignored) {
      return true;
    } finally {
      client.close();
    }
  }

  @DisplayName("multiple threads using the same builder works")
  @Test
  void testMultipleThreads() throws InterruptedException {
    URI uri = server.httpUri().resolve("/success");
    JerseyClientBuilder builder = new JerseyClientBuilder();
    AtomicBoolean hadErrors = new AtomicBoolean();

    // Start 10 threads and do 50 requests each
    CountDownLatch latch = new CountDownLatch(10);
    for (int i = 0; i < 10; i++) {
      new Thread(
              new Runnable() {
                @Override
                public void run() {
                  try {
                    for (int j = 0; j < 50; j++) {
                      if (checkUri(builder, uri)) {
                        hadErrors.set(true);
                        return;
                      }
                    }
                  } finally {
                    latch.countDown();
                  }
                }
              })
          .start();
    }

    assertThat(latch.await(10, SECONDS)).isTrue();
    assertThat(hadErrors).isFalse();
  }
}
