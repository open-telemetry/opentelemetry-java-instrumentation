/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2

import io.opentelemetry.instrumentation.test.LibraryTestTrait
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration

class Aws2ClientNotRecordHttpErrorTest extends AbstractAws2ClientRecordHttpErrorTest implements LibraryTestTrait {
  @Override
  ClientOverrideConfiguration.Builder createOverrideConfigurationBuilder() {
    return ClientOverrideConfiguration.builder()
      .addExecutionInterceptor(
        AwsSdkTelemetry.builder(getOpenTelemetry())
          .setCaptureExperimentalSpanAttributes(true)
            .setRecordIndividualHttpError(isRecordIndividualHttpErrorEnabled())
            .build()
          .newExecutionInterceptor())
  }

  @Override
  static boolean isRecordIndividualHttpErrorEnabled() {
    return false
  }

}
