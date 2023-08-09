/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2

import io.opentelemetry.instrumentation.test.LibraryTestTrait
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.services.sqs.SqsClient

class Aws2SqsTracingTest extends AbstractAws2SqsTracingTest implements LibraryTestTrait {
  @Override
  ClientOverrideConfiguration.Builder createOverrideConfigurationBuilder() {
    return ClientOverrideConfiguration.builder()
      .addExecutionInterceptor(
        AwsSdkTelemetry.builder(getOpenTelemetry())
          .setCaptureExperimentalSpanAttributes(true)
          .build()
          .newExecutionInterceptor())
  }

  @Override
  boolean isSqsAttributeInjectionEnabled() {
    false
  }

  def "duplicate tracing interceptor"() {
    setup:
    def builder = SqsClient.builder()
    configureSdkClient(builder)
    def telemetry = AwsSdkTelemetry.builder(getOpenTelemetry())
      .setCaptureExperimentalSpanAttributes(true)
      .build()
    def overrideConfiguration = ClientOverrideConfiguration.builder()
      .addExecutionInterceptor(telemetry.newExecutionInterceptor())
      .addExecutionInterceptor(telemetry.newExecutionInterceptor())
      .build()
    builder.overrideConfiguration(overrideConfiguration)
    def client = builder.build()

    client.createQueue(createQueueRequest)

    when:
    client.sendMessage(sendMessageRequest)

    def resp = client.receiveMessage(receiveMessageRequest)

    then:
    resp.messages().size() == 1
    assertSqsTraces()
  }
}
