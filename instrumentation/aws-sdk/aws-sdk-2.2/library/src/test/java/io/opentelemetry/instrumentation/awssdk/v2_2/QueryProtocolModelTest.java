/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;

public class QueryProtocolModelTest extends AbstractQueryProtocolModelTest {
  private final LibraryInstrumentationExtension extension =
      LibraryInstrumentationExtension.create();

  @Override
  protected ClientOverrideConfiguration.Builder createClientOverrideConfigurationBuilder() {
    return ClientOverrideConfiguration.builder()
        .addExecutionInterceptor(
            AwsSdkTelemetry.builder(extension.getOpenTelemetry())
                .build()
                .newExecutionInterceptor());
  }

  @Override
  protected InstrumentationExtension getInstrumentationExtension() {
    return extension;
  }
}
