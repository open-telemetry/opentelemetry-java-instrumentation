/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent.instrumentation;

import static io.opentelemetry.sdk.testing.assertj.TracesAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import java.io.IOException;
import java.io.Writer;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * This is a demo instrumentation test that verifies that the custom servlet instrumentation was
 * applied.
 */
class DemoServlet3InstrumentationTest {
  @RegisterExtension
  static final AgentInstrumentationExtension instrumentation =
      AgentInstrumentationExtension.create();

  static final OkHttpClient httpClient = new OkHttpClient();

  static int port;
  static Server server;

  @BeforeAll
  static void startServer() throws Exception {
    port = PortUtils.findOpenPort();
    server = new Server(port);
    for (var connector : server.getConnectors()) {
      connector.setHost("localhost");
    }

    var servletContext = new ServletContextHandler(null, null);
    servletContext.addServlet(DefaultServlet.class, "/");
    servletContext.addServlet(TestServlet.class, "/servlet");
    server.setHandler(servletContext);

    server.start();
  }

  @AfterAll
  static void stopServer() throws Exception {
    server.stop();
    server.destroy();
  }

  @Test
  void shouldAddCustomHeader() throws Exception {
    // given
    var request =
        new Request.Builder()
            .url(HttpUrl.get("http://localhost:" + port + "/servlet"))
            .get()
            .build();

    // when
    var response = httpClient.newCall(request).execute();

    // then
    assertEquals(200, response.code());
    assertEquals("result", response.body().string());

    assertThat(instrumentation.waitForTraces(1))
        .hasSize(1)
        .hasTracesSatisfyingExactly(
            trace ->
                trace
                    .hasSize(1)
                    .hasSpansSatisfyingExactly(
                        span -> span.hasName("/servlet").hasKind(SpanKind.SERVER)));

    var traceId = instrumentation.spans().get(0).getTraceId();
    assertEquals(traceId, response.header("X-server-id"));
  }

  public static class TestServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
      try (Writer writer = response.getWriter()) {
        writer.write("result");
        response.setStatus(200);
      }
    }
  }
}
