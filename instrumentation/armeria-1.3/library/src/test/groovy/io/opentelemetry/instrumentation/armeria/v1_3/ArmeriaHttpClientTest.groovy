/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3

import com.linecorp.armeria.client.WebClientBuilder
import io.opentelemetry.instrumentation.test.LibraryTestTrait

class ArmeriaHttpClientTest extends AbstractArmeriaHttpClientTest implements LibraryTestTrait {
  @Override
  WebClientBuilder configureClient(WebClientBuilder clientBuilder) {
    return clientBuilder.decorator(ArmeriaTracing.create(getOpenTelemetry()).newClientDecorator())
  }
}
