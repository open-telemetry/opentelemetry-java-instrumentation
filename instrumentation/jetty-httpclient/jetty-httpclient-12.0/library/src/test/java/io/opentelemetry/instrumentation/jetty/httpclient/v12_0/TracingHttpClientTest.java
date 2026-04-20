/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v12_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.lang.reflect.Field;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.transport.HttpClientTransportOverHTTP;
import org.junit.jupiter.api.Test;

class TracingHttpClientTest {

  @Test
  void buildNewUsesProvidedTransportWithoutSslContextFactory() throws ReflectiveOperationException {
    HttpClientTransportOverHTTP transport = new HttpClientTransportOverHTTP();
    TracingHttpClient client = TracingHttpClient.buildNew(mockInstrumenter(), null, transport);

    assertThat(getTransport(client)).isSameAs(transport);
  }

  @SuppressWarnings("unchecked")
  private static Instrumenter<Request, Response> mockInstrumenter() {
    return mock(Instrumenter.class);
  }

  private static Object getTransport(HttpClient client) throws ReflectiveOperationException {
    Field transport = HttpClient.class.getDeclaredField("transport");
    transport.setAccessible(true);
    return transport.get(client);
  }
}
