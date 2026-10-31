/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.appserver;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.smoketest.SmokeTestOptions;
import io.opentelemetry.smoketest.TargetWaitStrategy;
import java.net.Socket;
import java.time.Duration;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.impl.io.DefaultBHttpClientConnectionFactory;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.junit.jupiter.api.Test;

abstract class JettySmokeTest extends AppServerTest {

  @Test
  void injectsIntoWriterAfterReusingNonHtmlResponse() throws Exception {
    try (Socket socket = new Socket("localhost", getMappedPort(8080));
        var connection = new DefaultBHttpClientConnectionFactory().createConnection(socket)) {
      socket.setSoTimeout(30_000);
      HttpRequestExecutor executor = new HttpRequestExecutor();
      HttpCoreContext context = HttpCoreContext.create();
      BasicClassicHttpRequest staticRequest = new BasicClassicHttpRequest("GET", "/app/hello.txt");
      staticRequest.setHeader("Host", "localhost");
      try (ClassicHttpResponse response = executor.execute(staticRequest, connection, context)) {
        assertThat(response.getCode()).isEqualTo(200);
        byte[] body = EntityUtils.toByteArray(response.getEntity());
        assertThat(new String(body, UTF_8).trim()).isEqualTo("Hello");
        if (!isWindows) {
          assertThat(body).isEqualTo("Hello".getBytes(UTF_8));
        }
        assertThat(response.getFirstHeader("Content-Length")).isNotNull();
        assertContentLength(response, body);
      }

      BasicClassicHttpRequest jspRequest = new BasicClassicHttpRequest("GET", "/app/jsp");
      jspRequest.setHeader("Host", "localhost");
      try (ClassicHttpResponse response = executor.execute(jspRequest, connection, context)) {
        assertThat(response.getCode()).isEqualTo(200);
        byte[] body = EntityUtils.toByteArray(response.getEntity());
        assertThat(new String(body, UTF_8).replace("\r\n", "\n"))
            .isEqualTo(
                "\n\n<html>\n"
                    + "  <head><script>console.log(hi)</script>\n"
                    + "    <title>Successful JSP test</title>\n"
                    + "  </head>\n"
                    + "  <body>\n"
                    + "    This JSP demonstrates that Otel instrumentation agent does not break JSP compilation and loading.\n"
                    + "  </body>\n"
                    + "</html>\n");
        if (!isWindows) {
          assertThat(body).hasSize(228);
        }
        assertContentLength(response, body);
      }
    }
    testing.waitForTraces(2);
  }

  private static void assertContentLength(ClassicHttpResponse response, byte[] body) {
    var contentLength = response.getFirstHeader("Content-Length");
    if (contentLength != null) {
      assertThat(Long.parseLong(contentLength.getValue())).isEqualTo(body.length);
    }
  }

  @Override
  protected void configure(SmokeTestOptions<AppServerImage> options) {
    configureOptions(options);
  }

  static SmokeTestOptions<AppServerImage> configureOptions(
      SmokeTestOptions<AppServerImage> options) {
    return options
        .image(
            appServerImage(
                "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-servlet-jetty"))
        .waitStrategy(new TargetWaitStrategy.Log(Duration.ofMinutes(1), ".*Started Server.*"));
  }

  @AppServer(version = "9.4.58", jdk = "8", inLimitedMatrix = true)
  static class Jetty9Jdk8 extends JettySmokeTest {}

  @AppServer(version = "9.4.58", jdk = "8-openj9")
  static class Jetty9Jdk8Openj9 extends JettySmokeTest {}

  @AppServer(version = "9.4.58", jdk = "11")
  static class Jetty9Jdk11 extends JettySmokeTest {}

  @AppServer(version = "9.4.58", jdk = "11-openj9")
  static class Jetty9Jdk11Openj9 extends JettySmokeTest {}

  @AppServer(version = "9.4.58", jdk = "17")
  static class Jetty9Jdk17 extends JettySmokeTest {}

  @AppServer(version = "9.4.58", jdk = "17-openj9")
  static class Jetty9Jdk17Openj9 extends JettySmokeTest {}

  @AppServer(version = "9.4.58", jdk = "21")
  static class Jetty9Jdk21 extends JettySmokeTest {}

  @AppServer(version = "9.4.58", jdk = "21-openj9")
  static class Jetty9Jdk21Openj9 extends JettySmokeTest {}

  @AppServer(version = "9.4.58", jdk = "25")
  static class Jetty9Jdk25 extends JettySmokeTest {}

  @AppServer(version = "9.4.58", jdk = "25-openj9")
  static class Jetty9Jdk25Openj9 extends JettySmokeTest {}

  @AppServer(version = "9.4.58", jdk = "27")
  static class Jetty9Jdk27 extends JettySmokeTest {}

  @AppServer(version = "9.4.58", jdk = "27-openj9")
  static class Jetty9Jdk27Openj9 extends JettySmokeTest {}

  @AppServer(version = "10.0.26", jdk = "11", inLimitedMatrix = true)
  static class Jetty10Jdk11 extends JettySmokeTest {}

  @AppServer(version = "10.0.26", jdk = "11-openj9")
  static class Jetty10Jdk11Openj9 extends JettySmokeTest {}

  @AppServer(version = "10.0.26", jdk = "17")
  static class Jetty10Jdk17 extends JettySmokeTest {}

  @AppServer(version = "10.0.26", jdk = "17-openj9")
  static class Jetty10Jdk17Openj9 extends JettySmokeTest {}

  @AppServer(version = "10.0.26", jdk = "21")
  static class Jetty10Jdk21 extends JettySmokeTest {}

  @AppServer(version = "10.0.26", jdk = "21-openj9")
  static class Jetty10Jdk21Openj9 extends JettySmokeTest {}

  @AppServer(version = "10.0.26", jdk = "25")
  static class Jetty10Jdk25 extends JettySmokeTest {}

  @AppServer(version = "10.0.26", jdk = "25-openj9")
  static class Jetty10Jdk25Openj9 extends JettySmokeTest {}

  @AppServer(version = "10.0.26", jdk = "27")
  static class Jetty10Jdk27 extends JettySmokeTest {}

  @AppServer(version = "10.0.26", jdk = "27-openj9")
  static class Jetty10Jdk27Openj9 extends JettySmokeTest {}

  @AppServer(version = "11.0.26", jdk = "11", inLimitedMatrix = true)
  static class Jetty11Jdk11 extends JettySmokeTest {}

  @AppServer(version = "11.0.26", jdk = "11-openj9")
  static class Jetty11Jdk11Openj9 extends JettySmokeTest {}

  @AppServer(version = "11.0.26", jdk = "17")
  static class Jetty11Jdk17 extends JettySmokeTest {}

  @AppServer(version = "11.0.26", jdk = "17-openj9")
  static class Jetty11Jdk17Openj9 extends JettySmokeTest {}

  @AppServer(version = "11.0.26", jdk = "21")
  static class Jetty11Jdk21 extends JettySmokeTest {}

  @AppServer(version = "11.0.26", jdk = "21-openj9")
  static class Jetty11Jdk21Openj9 extends JettySmokeTest {}

  @AppServer(version = "11.0.26", jdk = "25")
  static class Jetty11Jdk25 extends JettySmokeTest {}

  @AppServer(version = "11.0.26", jdk = "25-openj9")
  static class Jetty11Jdk25Openj9 extends JettySmokeTest {}

  @AppServer(version = "11.0.26", jdk = "27")
  static class Jetty11Jdk27 extends JettySmokeTest {}

  @AppServer(version = "11.0.26", jdk = "27-openj9")
  static class Jetty11Jdk27Openj9 extends JettySmokeTest {}

  @AppServer(version = "12.0.28", jdk = "17", inLimitedMatrix = true)
  static class Jetty12Jdk17 extends JettySmokeTest {}

  @AppServer(version = "12.0.28", jdk = "17-openj9")
  static class Jetty12Jdk17Openj9 extends JettySmokeTest {}

  @AppServer(version = "12.0.28", jdk = "21")
  static class Jetty12Jdk21 extends JettySmokeTest {}

  @AppServer(version = "12.0.28", jdk = "21-openj9")
  static class Jetty12Jdk21Openj9 extends JettySmokeTest {}

  @AppServer(version = "12.0.28", jdk = "25")
  static class Jetty12Jdk25 extends JettySmokeTest {}

  @AppServer(version = "12.0.28", jdk = "25-openj9")
  static class Jetty12Jdk25Openj9 extends JettySmokeTest {}

  @AppServer(version = "12.0.28", jdk = "27", inLimitedMatrix = true)
  static class Jetty12Jdk27 extends JettySmokeTest {}

  @AppServer(version = "12.0.28", jdk = "27-openj9")
  static class Jetty12Jdk27Openj9 extends JettySmokeTest {}
}
