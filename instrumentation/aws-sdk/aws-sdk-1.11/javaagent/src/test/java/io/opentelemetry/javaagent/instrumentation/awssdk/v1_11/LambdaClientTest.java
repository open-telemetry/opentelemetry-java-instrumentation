/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import io.opentelemetry.instrumentation.awssdk.v1_11.AbstractLambdaClientTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class LambdaClientTest extends AbstractLambdaClientTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  public AWSLambdaClientBuilder configureClient(AWSLambdaClientBuilder clientBuilder) {
    return clientBuilder;
  }
}
