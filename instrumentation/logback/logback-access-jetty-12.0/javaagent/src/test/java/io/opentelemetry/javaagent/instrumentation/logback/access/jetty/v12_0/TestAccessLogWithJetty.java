/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.logback.access.jetty.v12_0;

import static io.opentelemetry.javaagent.instrumentation.logback.access.jetty.v12_0.AccessEventMapper.ACCESS_EVENT_NAME;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.qos.logback.access.common.spi.Util;
import ch.qos.logback.access.jetty.RequestLogImpl;
import ch.qos.logback.core.testUtil.RandomUtil;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.semconv.ClientAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.UserAgentAttributes;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class TestAccessLogWithJetty {

  static RequestLogImpl requestLogImpl = new RequestLogImpl();
  static JettyServer jettyFixture;
  static String jettyFixtureUrlStr;

  static final int RANDOM_SERVER_PORT = RandomUtil.getRandomServerPort();

  @BeforeEach
  void startServer() throws Exception {
    requestLogImpl = new RequestLogImpl();
    jettyFixture = new JettyServer(requestLogImpl, RANDOM_SERVER_PORT);
    jettyFixture.start();
    jettyFixtureUrlStr = jettyFixture.getUrl();
  }

  @AfterEach
  void stopServer() throws Exception {
    if (jettyFixture != null) {
      jettyFixture.stop();
    }
  }

  @RegisterExtension
  private static final AgentInstrumentationExtension testing =
      AgentInstrumentationExtension.create();

  @Test
  void testT() throws IOException {
    String path = "12312321";
    URL url = new URL("http://localhost:" + RANDOM_SERVER_PORT + "/" + path);
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setDoInput(true);

    String result = Util.readToString(connection.getInputStream());

    assertEquals("hello world", result);

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasEventName(ACCESS_EVENT_NAME)
                .hasSeverity(Severity.UNDEFINED_SEVERITY_NUMBER)
                .hasAttributesSatisfyingExactly(
                    equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "GET"),
                    equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, Long.valueOf(200)),
                    equalTo(ClientAttributes.CLIENT_ADDRESS, "127.0.0.1"),
                    equalTo(ServerAttributes.SERVER_ADDRESS, "localhost"),
                    equalTo(UserAgentAttributes.USER_AGENT_ORIGINAL, "-"),
                    equalTo(UrlAttributes.URL_PATH, "/" + path),
                    equalTo(UrlAttributes.URL_SCHEME, "http")));
  }
}
