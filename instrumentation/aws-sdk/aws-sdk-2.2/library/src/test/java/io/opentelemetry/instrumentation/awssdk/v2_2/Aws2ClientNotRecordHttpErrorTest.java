/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;

public class Aws2ClientNotRecordHttpErrorTest extends AbstractAws2ClientRecordHttpErrorTest {
  @RegisterExtension
  public static final LibraryInstrumentationExtension testing =
      LibraryInstrumentationExtension.create();

  @Override
  public ClientOverrideConfiguration.Builder createOverrideConfigurationBuilder() {
    return ClientOverrideConfiguration.builder()
        .addExecutionInterceptor(
            AwsSdkTelemetry.builder(testing.getOpenTelemetry())
                .setCaptureExperimentalSpanAttributes(true)
                .setRecordIndividualHttpError(isRecordIndividualHttpErrorEnabled())
                .build()
                .newExecutionInterceptor());
  }

  @Override
  public boolean isRecordIndividualHttpErrorEnabled() {
    return false;
  }

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }
}
