/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.http;

import io.opentelemetry.instrumentation.testing.AgentTestRunner;
import io.opentelemetry.instrumentation.testing.InstrumentationTestRunner;
import io.opentelemetry.instrumentation.testing.LibraryTestRunner;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * A {@link InstrumentationExtension} which sets up infrastructure, such as a test HTTP server, for
 * {@link AbstractHttpClientTest}.
 */
public final class HttpClientInstrumentationExtension extends InstrumentationExtension {

  /**
   * Returns a {@link InstrumentationExtension} to be used with {@link AbstractHttpClientTest} for
   * javaagent instrumentation.
   */
  public static InstrumentationExtension forAgent() {
    return new HttpClientInstrumentationExtension(AgentTestRunner.instance());
  }

  /**
   * Returns a {@link InstrumentationExtension} to be used with {@link AbstractHttpClientTest} for
   * library instrumentation.
   */
  public static InstrumentationExtension forLibrary() {
    return new HttpClientInstrumentationExtension(LibraryTestRunner.instance());
  }

  private final HttpClientTestServer server;

  private HttpClientInstrumentationExtension(InstrumentationTestRunner runner) {
    super(runner);

    server = new HttpClientTestServer(getOpenTelemetry());
  }

  @Override
  public void beforeAll(ExtensionContext extensionContext) throws Exception {
    super.beforeAll(extensionContext);
    server.beforeAll(extensionContext);
  }

  @Override
  public void beforeEach(ExtensionContext extensionContext) {
    super.beforeEach(extensionContext);
    Object testInstance = extensionContext.getRequiredTestInstance();

    if (!(testInstance instanceof AbstractHttpClientTest)) {
      throw new AssertionError(
          "HttpClientInstrumentationExtension can only be applied to a subclass of "
              + "AbstractHttpClientTest");
    }

    ((AbstractHttpClientTest<?>) testInstance).setTesting(getTestRunner(), server);
  }

  @Override
  public void afterAll(ExtensionContext extensionContext) throws Exception {
    super.afterAll(extensionContext);
    server.afterAll(extensionContext);
  }
}
