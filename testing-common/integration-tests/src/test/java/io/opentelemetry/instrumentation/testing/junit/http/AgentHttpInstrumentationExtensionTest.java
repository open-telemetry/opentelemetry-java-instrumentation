/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.testing.AgentTestRunner;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import io.opentelemetry.testing.internal.armeria.common.HttpHeaderNames;
import io.opentelemetry.testing.internal.armeria.common.HttpResponse;
import io.opentelemetry.testing.internal.armeria.server.Server;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.ArgumentCaptor;

class AgentHttpInstrumentationExtensionTest {

  @Test
  void clientFixtureLifecycle() throws Exception {
    InstrumentationExtension extension = AgentHttpClientInstrumentationExtension.create();
    assertThat(extension).isInstanceOf(AgentHttpClientInstrumentationExtension.class);
    assertThat(extension.getOpenTelemetry())
        .isSameAs(AgentTestRunner.instance().getOpenTelemetry());

    AbstractHttpClientTest<?> fixture = mock(AbstractHttpClientTest.class);
    ExtensionContext context = mock(ExtensionContext.class);
    when(context.getRequiredTestInstance()).thenReturn(fixture);
    extension.beforeAll(context);
    Server server;
    try {
      extension.beforeEach(context);
      ArgumentCaptor<HttpClientTestServer> serverCaptor =
          ArgumentCaptor.forClass(HttpClientTestServer.class);
      verify(fixture).setTesting(eq(AgentTestRunner.instance()), serverCaptor.capture());
      HttpClientTestServer testServer = serverCaptor.getValue();
      server = testServer.server();

      assertThat(
              WebClient.of()
                  .get(testServer.resolveAddress("/success").toString())
                  .aggregate()
                  .join()
                  .contentUtf8())
          .isEqualTo("Hello.");
      extension.waitAndAssertTraces(
          trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("test-http-server")));
      extension.afterEach(context);
      extension.beforeEach(context);
      assertThat(extension.spans()).isEmpty();
      extension.afterEach(context);
    } finally {
      extension.afterAll(context);
    }
    await().untilAsserted(() -> assertThat(server.activePorts()).isEmpty());
  }

  @Test
  void serverFixtureLifecycle() throws Exception {
    InstrumentationExtension extension = AgentHttpServerInstrumentationExtension.create();
    assertThat(extension).isInstanceOf(AgentHttpServerInstrumentationExtension.class);
    assertThat(extension.getOpenTelemetry())
        .isSameAs(AgentTestRunner.instance().getOpenTelemetry());

    AbstractHttpServerUsingTest<?> fixture = mock(AbstractHttpServerUsingTest.class);
    ExtensionContext context = mock(ExtensionContext.class);
    when(context.getRequiredTestInstance()).thenReturn(fixture);
    extension.beforeAll(context);
    try {
      ArgumentCaptor<WebClient> clientCaptor = ArgumentCaptor.forClass(WebClient.class);
      ArgumentCaptor<WebClient> plainClientCaptor = ArgumentCaptor.forClass(WebClient.class);
      ArgumentCaptor<Integer> portCaptor = ArgumentCaptor.forClass(Integer.class);
      verify(fixture)
          .setTesting(
              eq(AgentTestRunner.instance()),
              clientCaptor.capture(),
              plainClientCaptor.capture(),
              portCaptor.capture());
      Server server =
          Server.builder()
              .http(portCaptor.getValue())
              .service(
                  "/",
                  (ctx, req) ->
                      HttpResponse.of(
                          req.headers().get(HttpHeaderNames.USER_AGENT)
                              + ":"
                              + req.headers().get(HttpHeaderNames.X_FORWARDED_FOR)))
              .build();
      server.start().join();
      try {
        String url = "http://localhost:" + portCaptor.getValue() + "/";
        assertThat(clientCaptor.getValue().get(url).aggregate().join().contentUtf8())
            .isEqualTo("test-user-agent:1.1.1.1");
        assertThat(plainClientCaptor.getValue().get(url).aggregate().join().contentUtf8())
            .isEqualTo("test-user-agent:null");
      } finally {
        server.stop().join();
      }
      extension.beforeEach(context);
      extension.runWithSpan("server-fixture", () -> {});
      extension.waitAndAssertTraces(
          trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("server-fixture")));
      extension.afterEach(context);
      extension.beforeEach(context);
      assertThat(extension.spans()).isEmpty();
      extension.afterEach(context);
    } finally {
      extension.afterAll(context);
    }
  }
}
