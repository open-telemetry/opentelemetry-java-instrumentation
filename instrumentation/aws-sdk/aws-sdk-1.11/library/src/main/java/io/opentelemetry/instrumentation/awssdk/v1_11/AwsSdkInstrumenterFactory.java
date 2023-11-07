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
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
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
import javax.annotation.Nullable;

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
        emptyList(),
        true);
  }

  static Instrumenter<Request<?>, Response<?>> consumerReceiveInstrumenter(
      OpenTelemetry openTelemetry,
      boolean captureExperimentalSpanAttributes,
      boolean messagingReceiveInstrumentationEnabled) {
    return sqsInstrumenter(
        openTelemetry,
        MessageOperation.RECEIVE,
        captureExperimentalSpanAttributes,
        messagingReceiveInstrumentationEnabled);
  }

  static Instrumenter<SqsProcessRequest, Void> consumerProcessInstrumenter(
      OpenTelemetry openTelemetry,
      boolean captureExperimentalSpanAttributes,
      boolean messagingReceiveInstrumentationEnabled) {
    MessageOperation operation = MessageOperation.PROCESS;
    SqsProcessRequestAttributesGetter getter = SqsProcessRequestAttributesGetter.INSTANCE;

    InstrumenterBuilder<SqsProcessRequest, Void> builder =
        Instrumenter.<SqsProcessRequest, Void>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(getter, operation))
            .addAttributesExtractors(
                toProcessRequestExtractors(
                    captureExperimentalSpanAttributes
                        ? extendedAttributesExtractors
                        : defaultAttributesExtractors))
            .addAttributesExtractor(
                MessagingAttributesExtractor.builder(getter, operation).build());

    if (messagingReceiveInstrumentationEnabled) {
      builder.addSpanLinksExtractor(
          (spanLinks, parentContext, request) -> {
            Context extracted =
                SqsParentContext.ofSystemAttributes(request.getMessage().getAttributes());
            spanLinks.addLink(Span.fromContext(extracted).getSpanContext());
          });
    }
    return builder.buildInstrumenter(SpanKindExtractor.alwaysConsumer());
  }

  private static List<AttributesExtractor<SqsProcessRequest, Void>> toProcessRequestExtractors(
      List<AttributesExtractor<Request<?>, Response<?>>> extractors) {
    List<AttributesExtractor<SqsProcessRequest, Void>> result = new ArrayList<>();
    for (AttributesExtractor<Request<?>, Response<?>> extractor : extractors) {
      result.add(
          new AttributesExtractor<SqsProcessRequest, Void>() {
            @Override
            public void onStart(
                AttributesBuilder attributes,
                Context parentContext,
                SqsProcessRequest sqsProcessRequest) {
              extractor.onStart(attributes, parentContext, sqsProcessRequest.getRequest());
            }

            @Override
            public void onEnd(
                AttributesBuilder attributes,
                Context context,
                SqsProcessRequest sqsProcessRequest,
                @Nullable Void unused,
                @Nullable Throwable error) {
              extractor.onEnd(attributes, context, sqsProcessRequest.getRequest(), null, error);
            }
          });
    }
    return result;
  }

  static Instrumenter<Request<?>, Response<?>> producerInstrumenter(
      OpenTelemetry openTelemetry, boolean captureExperimentalSpanAttributes) {
    return sqsInstrumenter(
        openTelemetry, MessageOperation.PUBLISH, captureExperimentalSpanAttributes, true);
  }

  private static Instrumenter<Request<?>, Response<?>> sqsInstrumenter(
      OpenTelemetry openTelemetry,
      MessageOperation operation,
      boolean captureExperimentalSpanAttributes,
      boolean enabled) {
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
        singletonList(messagingAttributeExtractor),
        enabled);
  }

  private static Instrumenter<Request<?>, Response<?>> createInstrumenter(
      OpenTelemetry openTelemetry,
      boolean captureExperimentalSpanAttributes,
      SpanNameExtractor<Request<?>> spanNameExtractor,
      SpanKindExtractor<Request<?>> spanKindExtractor,
      List<AttributesExtractor<Request<?>, Response<?>>> additionalAttributeExtractors,
      boolean enabled) {
    return Instrumenter.<Request<?>, Response<?>>builder(
            openTelemetry, INSTRUMENTATION_NAME, spanNameExtractor)
        .addAttributesExtractors(
            captureExperimentalSpanAttributes
                ? extendedAttributesExtractors
                : defaultAttributesExtractors)
        .addAttributesExtractors(additionalAttributeExtractors)
        .setEnabled(enabled)
        .buildInstrumenter(spanKindExtractor);
  }

  private AwsSdkInstrumenterFactory() {}
}
