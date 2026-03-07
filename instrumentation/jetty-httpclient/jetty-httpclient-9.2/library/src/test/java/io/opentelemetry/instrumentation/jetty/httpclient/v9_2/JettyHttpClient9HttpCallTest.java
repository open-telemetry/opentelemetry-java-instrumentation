/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.util.InputStreamResponseListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Regression test for empty response body when OTel instrumentation wraps Jetty HttpClient
 * listeners on Jetty 9.4.24–~9.4.43.
 *
 * <p>In those versions, {@code AsyncContentListener} and {@code ContentListener} do NOT extend
 * {@code DemandedContentListener}. Jetty's {@code HttpReceiver.ContentListeners} filters via {@code
 * instanceof DemandedContentListener}, so a proxy that only implements the former interfaces gets
 * filtered out and content is never delivered.
 */
class JettyHttpClient9HttpCallTest {

  private static final String EXPECTED_BODY = "Hello World";

  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private HttpServer server;
  private HttpClient client;

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/",
        exchange -> {
          byte[] body = EXPECTED_BODY.getBytes(UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();

    client = JettyClientTelemetry.builder(testing.getOpenTelemetry()).build().createHttpClient();
    client.start();
  }

  @AfterEach
  void stop() throws Exception {
    if (client != null) {
      client.stop();
    }
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void contentResponseBodyIsDelivered() throws Exception {
    String url = "http://localhost:" + server.getAddress().getPort() + "/";

    ContentResponse response = client.newRequest(url).send();

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getContentAsString()).isEqualTo(EXPECTED_BODY);
  }

  @Test
  void inputStreamListenerBodyIsDelivered() throws Exception {
    String url = "http://localhost:" + server.getAddress().getPort() + "/";

    InputStreamResponseListener listener = new InputStreamResponseListener();
    client.newRequest(url).send(listener);

    Response response = listener.get(30, SECONDS);
    assertThat(response.getStatus()).isEqualTo(200);

    byte[] body;
    try (InputStream is = listener.getInputStream()) {
      ByteArrayOutputStream buf = new ByteArrayOutputStream();
      byte[] tmp = new byte[4096];
      int n;
      while ((n = is.read(tmp)) != -1) {
        buf.write(tmp, 0, n);
      }
      body = buf.toByteArray();
    }

    assertThat(body)
        .as(
            "Response body must not be empty — if it is, the OTel proxy is missing "
                + "DemandedContentListener and Jetty filters it out before delivering content")
        .isNotEmpty();
    assertThat(new String(body, UTF_8)).isEqualTo(EXPECTED_BODY);
  }
}
