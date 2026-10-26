/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsSdkTestUtil.EXPERIMENTAL_ATTRIBUTES;

import com.amazonaws.services.stepfunctions.AWSStepFunctionsClientBuilder;
import io.opentelemetry.instrumentation.awssdk.v1_11.AbstractStepFunctionsClientTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class StepFunctionsClientTest extends AbstractStepFunctionsClientTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected boolean captureExperimentalSpanAttributes() {
    return EXPERIMENTAL_ATTRIBUTES;
  }

  @Override
  public AWSStepFunctionsClientBuilder configureClient(
      AWSStepFunctionsClientBuilder clientBuilder) {
    return clientBuilder;
  }
}
