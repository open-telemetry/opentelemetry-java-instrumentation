/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.v1_3

import com.linecorp.armeria.client.WebClientBuilder
import io.opentelemetry.instrumentation.armeria.v1_3.AbstractArmeriaHttpClientTest
import io.opentelemetry.instrumentation.test.AgentTestTrait

class ArmeriaHttpClientTest extends AbstractArmeriaHttpClientTest implements AgentTestTrait {
  @Override
  WebClientBuilder configureClient(WebClientBuilder clientBuilder) {
    return clientBuilder
  }
}
