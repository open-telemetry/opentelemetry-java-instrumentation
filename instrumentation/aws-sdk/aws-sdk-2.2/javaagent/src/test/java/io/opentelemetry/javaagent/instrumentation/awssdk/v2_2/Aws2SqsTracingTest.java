/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v2_2;

import io.opentelemetry.instrumentation.awssdk.v2_2.AbstractAws2SqsTracingTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsClient;

class Aws2SqsTracingTest extends AbstractAws2SqsTracingTest {

  @RegisterExtension
  private static final AgentInstrumentationExtension testing =
      AgentInstrumentationExtension.create();

  @Override
  protected final AgentInstrumentationExtension getTesting() {
    return testing;
  }

  @Override
  protected ClientOverrideConfiguration.Builder createOverrideConfigurationBuilder() {
    return ClientOverrideConfiguration.builder();
  }

  @Override
  protected SqsClient configureSqsClient(SqsClient sqsClient) {
    return sqsClient;
  }

  @Override
  protected SqsAsyncClient configureSqsClient(SqsAsyncClient sqsClient) {
    return sqsClient;
  }
}
