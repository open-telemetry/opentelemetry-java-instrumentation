/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.tomcat;

import static io.opentelemetry.instrumentation.servlet.v3_0.AbstractServlet3Test.HTML_PRINT_WRITER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.servlet.v3_0.TestServlet3;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.bootstrap.servlet.ExperimentalSnippetHolder;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.servlet.Servlet;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Response;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

class Tomcat9NonPublicResponseContentLengthTest {
  private static final String SNIPPET = "<script>tomcat9</script>";

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @TempDir private static File tempDir;

  private static Tomcat tomcat;
  private static int port;

  @BeforeAll
  static void startServer() throws Exception {
    tomcat = new Tomcat();
    tomcat.setBaseDir(tempDir.getAbsolutePath());
    Connector connector =
        new Connector() {
          @Override
          public Response createResponse() {
            return new NonPublicResponse();
          }
        };
    connector.setPort(0);
    tomcat.setConnector(connector);
    Context context = tomcat.addContext("", tempDir.getAbsolutePath());
    addServlet(context, "/htmlPrintWriter", TestServlet3.Sync.class);
    tomcat.start();
    port = connector.getLocalPort();
  }

  @AfterAll
  static void stopServer() throws LifecycleException {
    tomcat.stop();
    tomcat.destroy();
  }

  @BeforeEach
  void setSnippet() {
    ExperimentalSnippetHolder.setSnippet(SNIPPET);
  }

  @AfterEach
  void clearSnippet() {
    ExperimentalSnippetHolder.setSnippet("");
  }

  @Test
  void adjustsNumericContentLengthForNonPublicResponseSubclass() throws Exception {
    String body = HTML_PRINT_WRITER.getBody().replace("<head>", "<head>" + SNIPPET);
    HttpURLConnection connection =
        (HttpURLConnection)
            new URL("http://localhost:" + port + "/htmlPrintWriter").openConnection();
    try {
      byte[] actual;
      try (InputStream input = connection.getInputStream();
          ByteArrayOutputStream output = new ByteArrayOutputStream()) {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
          output.write(buffer, 0, read);
        }
        actual = output.toByteArray();
      }

      byte[] expected = body.getBytes(UTF_8);
      assertThat(connection.getResponseCode()).isEqualTo(200);
      assertThat(actual).isEqualTo(expected);
      assertThat(connection.getHeaderField("Content-Length"))
          .isEqualTo(String.valueOf(expected.length));
    } finally {
      connection.disconnect();
    }
  }

  private static void addServlet(
      Context context, String path, Class<? extends Servlet> servletClass) throws Exception {
    String name = servletClass.getName() + path;
    Tomcat.addServlet(context, name, servletClass.getConstructor().newInstance());
    context.addServletMappingDecoded(path, name);
  }

  private static class NonPublicResponse extends Response {}
}
