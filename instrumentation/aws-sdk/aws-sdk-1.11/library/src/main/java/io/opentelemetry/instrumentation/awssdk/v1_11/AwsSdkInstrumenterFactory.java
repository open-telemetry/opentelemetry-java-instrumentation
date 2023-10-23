/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import com.amazonaws.Request;
import com.amazonaws.Response;
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
import java.util.Arrays;
import java.util.List;

final class AwsSdkInstrumenterFactory {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.aws-sdk-1.11";

  private static final AttributesExtractor<Request<?>, Response<?>> httpAttributesExtractor =
      HttpClientAttributesExtractor.create(new AwsSdkHttpAttributesGetter());
  private static final AttributesExtractor<Request<?>, Response<?>> rpcAttributesExtractor =
      RpcClientAttributesExtractor.create(AwsSdkRpcAttributesGetter.INSTANCE);
  private static final AwsSdkExperimentalAttributesExtractor experimentalAttributesExtractor =
      new AwsSdkExperimentalAttributesExtractor();

  private static final List<AttributesExtractor<Request<?>, Response<?>>>
      defaultAttributesExtractors = Arrays.asList(httpAttributesExtractor, rpcAttributesExtractor);
  private static final List<AttributesExtractor<Request<?>, Response<?>>>
      extendedAttributesExtractors =
          Arrays.asList(
              httpAttributesExtractor, rpcAttributesExtractor, experimentalAttributesExtractor);
  private static final AwsSdkSpanNameExtractor spanName = new AwsSdkSpanNameExtractor();

  static Instrumenter<Request<?>, Response<?>> requestInstrumenter(
      OpenTelemetry openTelemetry, boolean captureExperimentalSpanAttributes) {

    return createInstrumenter(
        openTelemetry,
        captureExperimentalSpanAttributes,
        spanName,
        SpanKindExtractor.alwaysClient(),
        emptyList());
  }

  static Instrumenter<Request<?>, Response<?>> consumerInstrumenter(
      OpenTelemetry openTelemetry, boolean captureExperimentalSpanAttributes) {
    return sqsInstrumenter(
        openTelemetry, MessageOperation.RECEIVE, captureExperimentalSpanAttributes);
  }

  static Instrumenter<Request<?>, Response<?>> producerInstrumenter(
      OpenTelemetry openTelemetry, boolean captureExperimentalSpanAttributes) {
    return sqsInstrumenter(
        openTelemetry, MessageOperation.PUBLISH, captureExperimentalSpanAttributes);
  }

  private static Instrumenter<Request<?>, Response<?>> sqsInstrumenter(
      OpenTelemetry openTelemetry,
      MessageOperation operation,
      boolean captureExperimentalSpanAttributes) {
    SqsAttributesGetter getter = SqsAttributesGetter.INSTANCE;
    AttributesExtractor<Request<?>, Response<?>> messagingAttributeExtractor =
        MessagingAttributesExtractor.builder(getter, operation).build();

    return createInstrumenter(
        openTelemetry,
        captureExperimentalSpanAttributes,
        MessagingSpanNameExtractor.create(getter, operation),
        operation == MessageOperation.PUBLISH
            ? SpanKindExtractor.alwaysProducer()
            : SpanKindExtractor.alwaysConsumer(),
        singletonList(messagingAttributeExtractor));
  }

  private static Instrumenter<Request<?>, Response<?>> createInstrumenter(
      OpenTelemetry openTelemetry,
      boolean captureExperimentalSpanAttributes,
      SpanNameExtractor<Request<?>> spanNameExtractor,
      SpanKindExtractor<Request<?>> spanKindExtractor,
      List<AttributesExtractor<Request<?>, Response<?>>> additionalAttributeExtractors) {
    return Instrumenter.<Request<?>, Response<?>>builder(
            openTelemetry, INSTRUMENTATION_NAME, spanNameExtractor)
        .addAttributesExtractors(
            captureExperimentalSpanAttributes
                ? extendedAttributesExtractors
                : defaultAttributesExtractors)
        .addAttributesExtractors(additionalAttributeExtractors)
        .buildInstrumenter(spanKindExtractor);
  }

  private AwsSdkInstrumenterFactory() {}
}
