/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.http;

import io.opentelemetry.instrumentation.testing.AgentTestRunner;

/**
 * JUnit 5 extension for {@link AbstractHttpClientTest} with javaagent instrumentation.
 *
 * <p>Requires running the test process with the {@code agent-for-testing} javaagent.
 */
public final class AgentHttpClientInstrumentationExtension
    extends HttpClientInstrumentationExtension {

  public static AgentHttpClientInstrumentationExtension create() {
    return new AgentHttpClientInstrumentationExtension();
  }

  private AgentHttpClientInstrumentationExtension() {
    super(AgentTestRunner.instance());
  }
}
