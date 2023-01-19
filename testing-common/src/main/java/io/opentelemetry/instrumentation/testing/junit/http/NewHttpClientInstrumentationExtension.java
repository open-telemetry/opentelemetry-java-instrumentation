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
public final class NewHttpClientInstrumentationExtension extends InstrumentationExtension {

  /**
   * Returns a {@link NewHttpClientInstrumentationExtension} to be used with {@link AbstractHttpClientTest} for
   * javaagent instrumentation.
   */
  public static NewHttpClientInstrumentationExtension forAgent() {
    return new NewHttpClientInstrumentationExtension(AgentTestRunner.instance());
  }

  /**
   * Returns a {@link NewHttpClientInstrumentationExtension} to be used with {@link AbstractHttpClientTest} for
   * library instrumentation.
   */
  public static NewHttpClientInstrumentationExtension forLibrary() {
    return new NewHttpClientInstrumentationExtension(LibraryTestRunner.instance());
  }

  private final HttpClientTestServer server;

  private NewHttpClientInstrumentationExtension(InstrumentationTestRunner runner) {
    super(runner);
    server = new HttpClientTestServer(getOpenTelemetry());
  }

  @Override
  public void beforeAll(ExtensionContext extensionContext) throws Exception {
    super.beforeAll(extensionContext);
    server.beforeAll(extensionContext);
  }

  public HttpClientTestServer getServer() {
    return server;
  }

  public InstrumentationTestRunner getTestRunner() {
    return super.getTestRunner();
  }

  @Override
  public void afterAll(ExtensionContext extensionContext) throws Exception {
    super.afterAll(extensionContext);
    server.afterAll(extensionContext);
  }
}
