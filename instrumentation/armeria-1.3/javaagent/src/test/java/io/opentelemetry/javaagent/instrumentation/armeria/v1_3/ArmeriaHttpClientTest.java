/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.v1_3;

import com.linecorp.armeria.client.WebClientBuilder;
import io.opentelemetry.instrumentation.armeria.v1_3.AbstractArmeriaHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.HttpClientAgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class ArmeriaHttpClientTest extends AbstractArmeriaHttpClientTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientAgentInstrumentationExtension.create();

  @Override
  protected WebClientBuilder configureClient(WebClientBuilder clientBuilder) {
    return clientBuilder;
  }
}
