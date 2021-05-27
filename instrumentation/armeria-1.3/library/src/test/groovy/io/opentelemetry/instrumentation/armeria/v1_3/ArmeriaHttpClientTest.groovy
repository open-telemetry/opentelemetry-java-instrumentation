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

  // library instrumentation doesn't have a good way of suppressing nested CLIENT spans yet
  @Override
  boolean testWithClientParent() {
    false
  }

  // Agent users have automatic propagation through executor instrumentation, but library users
  // should do manually using Armeria patterns.
  @Override
  boolean testCallbackWithParent() {
    false
  }

  @Override
  boolean testErrorWithCallback() {
    return false
  }
}
