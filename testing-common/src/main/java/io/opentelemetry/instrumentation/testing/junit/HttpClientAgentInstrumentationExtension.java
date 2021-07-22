/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit;

import io.opentelemetry.instrumentation.testing.AgentTestRunner;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class HttpClientAgentInstrumentationExtension extends InstrumentationExtension {

  public static InstrumentationExtension create() {
    return new HttpClientAgentInstrumentationExtension();
  }

  private final HttpClientTestServer server;

  private HttpClientAgentInstrumentationExtension() {
    super(AgentTestRunner.instance());

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
          "HttpClientLibraryInstrumentationExtension can only be applied to a subclass of "
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
