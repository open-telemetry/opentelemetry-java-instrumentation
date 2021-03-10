/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11

import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder
import io.opentelemetry.instrumentation.awssdk.v1_11.AbstractSqsTracingTest
import io.opentelemetry.instrumentation.test.AgentTestTrait

class SqsTracingTest extends AbstractSqsTracingTest implements AgentTestTrait {
  @Override
  AmazonSQSAsyncClientBuilder configureClient(AmazonSQSAsyncClientBuilder client) {
    return client
  }
}
