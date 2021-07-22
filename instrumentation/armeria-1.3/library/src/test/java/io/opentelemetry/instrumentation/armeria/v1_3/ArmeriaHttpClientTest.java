/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import com.linecorp.armeria.client.WebClientBuilder;
import io.opentelemetry.instrumentation.testing.junit.HttpClientLibraryInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class ArmeriaHttpClientTest extends AbstractArmeriaHttpClientTest {

  @RegisterExtension
  static final InstrumentationExtension testing =
      HttpClientLibraryInstrumentationExtension.create();

  @Override
  protected WebClientBuilder configureClient(WebClientBuilder clientBuilder) {
    return clientBuilder.decorator(
        ArmeriaTracing.create(testing.getOpenTelemetry()).newClientDecorator());
  }

  // library instrumentation doesn't have a good way of suppressing nested CLIENT spans yet
  @Override
  protected boolean testWithClientParent() {
    return false;
  }

  // Agent users have automatic propagation through executor instrumentation, but library users
  // should do manually using Armeria patterns.
  @Override
  protected boolean testCallbackWithParent() {
    return false;
  }

  @Override
  protected boolean testErrorWithCallback() {
    return false;
  }
}
