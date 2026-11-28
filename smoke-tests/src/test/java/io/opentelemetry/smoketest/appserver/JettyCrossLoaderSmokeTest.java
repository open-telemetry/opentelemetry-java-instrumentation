/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.appserver;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.smoketest.SmokeTestOptions;
import org.junit.jupiter.api.Test;

abstract class JettyCrossLoaderSmokeTest extends JettySmokeTest {
  @Override
  protected void configure(SmokeTestOptions<AppServerImage> options) {
    super.configure(options);
    // Otherwise Jetty's filter wraps the response before the WAR filter on both dispatches.
    options.env(
        "OTEL_JAVAAGENT_EXCLUDE_CLASSES",
        "org.eclipse.jetty.websocket.servlet.WebSocketUpgradeFilter");
  }

  @Test
  void reacquiresStreamAfterCrossLoaderAsyncDispatch() {
    var response = client().get("/app/async-stream-snippet/filter").aggregate().join();
    String body = response.contentUtf8();

    assertThat(response.status().isSuccess()).isTrue();
    assertThat(body)
        .isEqualTo(
            "<html><head><script>console.log(hi)</script><title>Async stream injection</title>"
                + "</head><body>done</body></html>");
    assertThat(body).containsOnlyOnce("<script>console.log(hi)</script>");
    testing.waitForTraces(1);
  }

  @AppServer(version = "10.0.26", jdk = "11", inLimitedMatrix = true)
  static class Jetty10Jdk11 extends JettyCrossLoaderSmokeTest {}

  @AppServer(version = "11.0.26", jdk = "11", inLimitedMatrix = true)
  static class Jetty11Jdk11 extends JettyCrossLoaderSmokeTest {}
}
