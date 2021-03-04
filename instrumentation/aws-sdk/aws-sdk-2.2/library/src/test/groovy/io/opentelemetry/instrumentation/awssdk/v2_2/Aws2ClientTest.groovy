/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2

import io.opentelemetry.instrumentation.test.LibraryTestTrait
import software.amazon.awssdk.core.client.builder.SdkClientBuilder
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration

class Aws2ClientTest extends AbstractAws2ClientTest implements LibraryTestTrait {
  @Override
  void configureSdkClient(SdkClientBuilder builder) {
    builder.overrideConfiguration(ClientOverrideConfiguration.builder()
      .addExecutionInterceptor(
        AwsSdkTracing.newBuilder(getOpenTelemetry())
          .setCaptureExperimentalSpanAttributes(true)
          .build()
          .newExecutionInterceptor())
      .build())
  }
}
