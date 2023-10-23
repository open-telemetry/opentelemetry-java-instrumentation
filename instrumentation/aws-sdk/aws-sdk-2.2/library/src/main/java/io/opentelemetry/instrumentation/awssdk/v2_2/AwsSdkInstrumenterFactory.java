/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcClientAttributesExtractor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;

final class AwsSdkInstrumenterFactory {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.aws-sdk-2.2";

  private static final AttributesExtractor<ExecutionAttributes, Response> rpcAttributesExtractor =
      RpcClientAttributesExtractor.create(AwsSdkRpcAttributesGetter.INSTANCE);
  private static final AwsSdkExperimentalAttributesExtractor experimentalAttributesExtractor =
      new AwsSdkExperimentalAttributesExtractor();

  static final AwsSdkHttpAttributesGetter httpAttributesGetter = new AwsSdkHttpAttributesGetter();
  static final AttributesExtractor<ExecutionAttributes, Response> httpAttributesExtractor =
      HttpClientAttributesExtractor.create(httpAttributesGetter);

  private static final AttributesExtractor<ExecutionAttributes, Response>
      httpClientSuppressionAttributesExtractor =
          new AwsSdkHttpClientSuppressionAttributesExtractor();

  private static final List<AttributesExtractor<ExecutionAttributes, Response>>
      defaultAttributesExtractors =
          Arrays.asList(rpcAttributesExtractor, httpClientSuppressionAttributesExtractor);

  private static final List<AttributesExtractor<ExecutionAttributes, Response>>
      extendedAttributesExtractors =
          Arrays.asList(
              rpcAttributesExtractor,
              experimentalAttributesExtractor,
              httpClientSuppressionAttributesExtractor);

  private static final List<AttributesExtractor<ExecutionAttributes, Response>>
      defaultConsumerAttributesExtractors =
          Arrays.asList(rpcAttributesExtractor, httpAttributesExtractor);

  private static final List<AttributesExtractor<ExecutionAttributes, Response>>
      extendedConsumerAttributesExtractors =
          Arrays.asList(
              rpcAttributesExtractor, httpAttributesExtractor, experimentalAttributesExtractor);

  static Instrumenter<ExecutionAttributes, Response> requestInstrumenter(
      OpenTelemetry openTelemetry, boolean captureExperimentalSpanAttributes) {

    return createInstrumenter(
        openTelemetry,
        captureExperimentalSpanAttributes
            ? extendedAttributesExtractors
            : defaultAttributesExtractors,
        AwsSdkInstrumenterFactory::spanName,
        SpanKindExtractor.alwaysClient());
  }

  static Instrumenter<ExecutionAttributes, Response> consumerInstrumenter(
      OpenTelemetry openTelemetry, boolean captureExperimentalSpanAttributes) {
    return sqsInstrumenter(
        openTelemetry,
        MessageOperation.RECEIVE,
        captureExperimentalSpanAttributes
            ? extendedConsumerAttributesExtractors
            : defaultConsumerAttributesExtractors);
  }

  static Instrumenter<ExecutionAttributes, Response> producerInstrumenter(
      OpenTelemetry openTelemetry, boolean captureExperimentalSpanAttributes) {
    return sqsInstrumenter(
        openTelemetry,
        MessageOperation.PUBLISH,
        captureExperimentalSpanAttributes
            ? extendedAttributesExtractors
            : defaultAttributesExtractors);
  }

  private static Instrumenter<ExecutionAttributes, Response> sqsInstrumenter(
      OpenTelemetry openTelemetry,
      MessageOperation operation,
      List<AttributesExtractor<ExecutionAttributes, Response>> extractors) {
    SqsAttributesGetter getter = SqsAttributesGetter.INSTANCE;
    AttributesExtractor<ExecutionAttributes, Response> messagingAttributeExtractor =
        MessagingAttributesExtractor.builder(getter, operation).build();
    List<AttributesExtractor<ExecutionAttributes, Response>> newExtractors =
        new ArrayList<>(extractors);
    newExtractors.add(messagingAttributeExtractor);

    return createInstrumenter(
        openTelemetry,
        newExtractors,
        MessagingSpanNameExtractor.create(getter, operation),
        operation == MessageOperation.PUBLISH
            ? SpanKindExtractor.alwaysProducer()
            : SpanKindExtractor.alwaysConsumer());
  }

  private static Instrumenter<ExecutionAttributes, Response> createInstrumenter(
      OpenTelemetry openTelemetry,
      List<AttributesExtractor<ExecutionAttributes, Response>> extractors,
      SpanNameExtractor<ExecutionAttributes> spanNameExtractor,
      SpanKindExtractor<ExecutionAttributes> spanKindExtractor) {

    return Instrumenter.<ExecutionAttributes, Response>builder(
            openTelemetry,
            INSTRUMENTATION_NAME,
            spanNameExtractor) // AwsSdkInstrumenterFactory::spanName
        .addAttributesExtractors(extractors)
        .buildInstrumenter(spanKindExtractor);
  }

  private static String spanName(ExecutionAttributes attributes) {
    String awsServiceName = attributes.getAttribute(SdkExecutionAttribute.SERVICE_NAME);
    String awsOperation = attributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME);
    return awsServiceName + "." + awsOperation;
  }

  private AwsSdkInstrumenterFactory() {}
}
