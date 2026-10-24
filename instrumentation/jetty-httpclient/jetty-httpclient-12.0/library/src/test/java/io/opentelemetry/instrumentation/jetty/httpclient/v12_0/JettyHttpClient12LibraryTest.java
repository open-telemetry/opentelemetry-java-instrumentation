/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v12_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Origin;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.transport.HttpClientTransportOverHTTP;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JettyHttpClient12LibraryTest extends AbstractJettyClient12Test {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forLibrary();

  @Override
  protected HttpClient createStandardClient() {
    return JettyClientTelemetry.builder(testing.getOpenTelemetry())
        .setRequestHeaders(AbstractHttpClientTest.TEST_HEADERS)
        .setResponseHeaders(AbstractHttpClientTest.TEST_HEADERS)
        .build()
        .createHttpClient();
  }

  // exercises the createHttpClient(HttpClientTransport) entry point as well
  @Override
  protected HttpClient createHttpsClient(SslContextFactory.Client sslContextFactory) {
    HttpClient client =
        JettyClientTelemetry.builder(testing.getOpenTelemetry())
            .build()
            .createHttpClient(new HttpClientTransportOverHTTP());
    client.setSslContextFactory(sslContextFactory);
    return client;
  }

  @Test
  void delegatedSendUsesClientContext() throws Exception {
    AtomicReference<Context> preparationContext = new AtomicReference<>();
    HttpClient client =
        JettyClientTelemetry.create(testing.getOpenTelemetry())
            .createHttpClient(
                new HttpClientTransportOverHTTP() {
                  @Override
                  public Origin newOrigin(Request request) {
                    preparationContext.set(Context.current());
                    return super.newOrigin(request);
                  }
                });
    cleanup.deferCleanup(client::stop);
    client.start();

    testing.runWithSpan(
        "parent",
        () -> {
          Context caller = Context.current();
          assertThat(client.newRequest(resolveAddress("/success")).send().getStatus())
              .isEqualTo(200);
          assertThat(Context.current()).isSameAs(caller);
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .satisfies(
                            spanData ->
                                assertThat(
                                        Span.fromContext(preparationContext.get()).getSpanContext())
                                    .isEqualTo(spanData.getSpanContext())),
                span -> span.hasName("test-http-server").hasParent(trace.getSpan(1))));
  }
}
