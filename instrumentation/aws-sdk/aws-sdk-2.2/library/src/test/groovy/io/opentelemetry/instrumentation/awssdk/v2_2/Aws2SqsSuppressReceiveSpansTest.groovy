/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2

import io.opentelemetry.instrumentation.test.LibraryTestTrait
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.SqsClient

abstract class Aws2SqsSuppressReceiveSpansTest extends AbstractAws2SqsSuppressReceiveSpansTest implements LibraryTestTrait {
  static AwsSdkTelemetry telemetry

  def setupSpec() {
    def telemetryBuilder = AwsSdkTelemetry.builder(getOpenTelemetry())
      .setCaptureExperimentalSpanAttributes(true)
    configure(telemetryBuilder)
    telemetry = telemetryBuilder.build()
  }

  abstract void configure(AwsSdkTelemetryBuilder telemetryBuilder)

  @Override
  ClientOverrideConfiguration.Builder createOverrideConfigurationBuilder() {
    return ClientOverrideConfiguration.builder()
      .addExecutionInterceptor(
        telemetry.newExecutionInterceptor())
  }

  @Override
  SqsClient configureSqsClient(SqsClient sqsClient) {
    return telemetry.wrap(sqsClient)
  }

  @Override
  SqsAsyncClient configureSqsClient(SqsAsyncClient sqsClient) {
    return telemetry.wrap(sqsClient)
  }
}

class Aws2SqsSuppressReceiveSpansDefaultPropagatorTest extends Aws2SqsSuppressReceiveSpansTest {

  @Override
  void configure(AwsSdkTelemetryBuilder telemetryBuilder) {}

  @Override
  boolean isSqsAttributeInjectionEnabled() {
    false
  }

  def "duplicate tracing interceptor"() {
    setup:
    def builder = SqsClient.builder()
    configureSdkClient(builder)
    def overrideConfiguration = ClientOverrideConfiguration.builder()
      .addExecutionInterceptor(telemetry.newExecutionInterceptor())
      .addExecutionInterceptor(telemetry.newExecutionInterceptor())
      .build()
    builder.overrideConfiguration(overrideConfiguration)
    def client = configureSqsClient(builder.build())

    client.createQueue(createQueueRequest)

    when:
    client.sendMessage(sendMessageRequest)

    def resp = client.receiveMessage(receiveMessageRequest)

    then:
    resp.messages().size() == 1
    resp.messages.each {message -> runWithSpan("process child") {}}
    assertSqsTraces()
  }
}

class Aws2SqsSuppressReceiveSpansW3CPropagatorTest extends Aws2SqsSuppressReceiveSpansTest {

  @Override
  void configure(AwsSdkTelemetryBuilder telemetryBuilder) {
    telemetryBuilder.setUseConfiguredPropagatorForMessaging(isSqsAttributeInjectionEnabled()) // Difference to main test
      .setUseXrayPropagator(isXrayInjectionEnabled()) // Disable to confirm messaging propagator actually works
  }

  @Override
  boolean isSqsAttributeInjectionEnabled() {
    true
  }

  @Override
  boolean isXrayInjectionEnabled() {
    false
  }
}

/** We want to test the combination of W3C + Xray, as that's what you'll get in prod if you enable W3C. */
class Aws2SqsSuppressReceiveSpansW3CPropagatorAndXrayPropagatorTest extends Aws2SqsSuppressReceiveSpansTest {

  @Override
  void configure(AwsSdkTelemetryBuilder telemetryBuilder) {
    telemetryBuilder.setUseConfiguredPropagatorForMessaging(isSqsAttributeInjectionEnabled()) // Difference to main test
  }

  @Override
  boolean isSqsAttributeInjectionEnabled() {
    true
  }
}
