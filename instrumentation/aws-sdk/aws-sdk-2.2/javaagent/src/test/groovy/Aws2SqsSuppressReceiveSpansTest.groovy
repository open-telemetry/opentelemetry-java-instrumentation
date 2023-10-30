/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.awssdk.v2_2.AbstractAws2SqsSuppressReceiveSpansTest
import io.opentelemetry.instrumentation.test.AgentTestTrait
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.SqsClient

class Aws2SqsSuppressReceiveSpansTest extends AbstractAws2SqsSuppressReceiveSpansTest implements AgentTestTrait {
  @Override
  ClientOverrideConfiguration.Builder createOverrideConfigurationBuilder() {
    return ClientOverrideConfiguration.builder()
  }

  @Override
  SqsClient configureSqsClient(SqsClient sqsClient) {
    return sqsClient
  }

  @Override
  SqsAsyncClient configureSqsClient(SqsAsyncClient sqsClient) {
    return sqsClient
  }
}
