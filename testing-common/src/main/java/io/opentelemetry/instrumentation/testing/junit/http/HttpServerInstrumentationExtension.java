/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.http;

import static io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest.TEST_CLIENT_IP;
import static io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest.TEST_USER_AGENT;

import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.AgentTestRunner;
import io.opentelemetry.instrumentation.testing.InstrumentationTestRunner;
import io.opentelemetry.instrumentation.testing.LibraryTestRunner;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.testing.internal.armeria.client.ClientFactory;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import io.opentelemetry.testing.internal.armeria.client.logging.LoggingClient;
import io.opentelemetry.testing.internal.armeria.common.HttpHeaderNames;
import java.time.Duration;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * A {@link InstrumentationExtension} which sets up infrastructure, such as a test HTTP client, for
 * {@link AbstractHttpServerTest}.
 */
public final class HttpServerInstrumentationExtension extends InstrumentationExtension {

  /**
   * Returns a {@link InstrumentationExtension} to be used with {@link AbstractHttpClientTest} for
   * javaagent instrumentation.
   */
  public static InstrumentationExtension forAgent() {
    return new HttpServerInstrumentationExtension(AgentTestRunner.instance());
  }

  /**
   * Returns a {@link InstrumentationExtension} to be used with {@link AbstractHttpClientTest} for
   * library instrumentation.
   */
  public static InstrumentationExtension forLibrary() {
    return new HttpServerInstrumentationExtension(LibraryTestRunner.instance());
  }

  private final int port;
  private final WebClient client;

  private HttpServerInstrumentationExtension(InstrumentationTestRunner runner) {
    super(runner);

    port = PortUtils.findOpenPort();
    client =
        WebClient.builder()
            .responseTimeout(Duration.ofMinutes(1))
            .writeTimeout(Duration.ofMinutes(1))
            .factory(ClientFactory.builder().connectTimeout(Duration.ofMinutes(1)).build())
            .setHeader(HttpHeaderNames.USER_AGENT, TEST_USER_AGENT)
            .setHeader(HttpHeaderNames.X_FORWARDED_FOR, TEST_CLIENT_IP)
            .decorator(LoggingClient.newDecorator())
            .build();
  }

  @Override
  public void beforeAll(ExtensionContext extensionContext) throws Exception {
    super.beforeAll(extensionContext);
    Object testInstance = extensionContext.getRequiredTestInstance();

    if (!(testInstance instanceof AbstractHttpServerTest)) {
      throw new AssertionError(
          "HttpServerInstrumentationExtension can only be applied to a subclass of "
              + "AbstractHttpServerTest");
    }

    ((AbstractHttpServerTest) testInstance).setTesting(getTestRunner(), client, port);
  }
}
