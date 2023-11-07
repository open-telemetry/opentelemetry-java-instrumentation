/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11

import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder
import io.opentelemetry.instrumentation.awssdk.v1_11.AbstractSqsSuppressReceiveSpansTest
import io.opentelemetry.instrumentation.test.AgentTestTrait

class SqsSuppressReceiveSpansTest extends AbstractSqsSuppressReceiveSpansTest implements AgentTestTrait {
  @Override
  AmazonSQSAsyncClientBuilder configureClient(AmazonSQSAsyncClientBuilder client) {
    return client
  }
}
