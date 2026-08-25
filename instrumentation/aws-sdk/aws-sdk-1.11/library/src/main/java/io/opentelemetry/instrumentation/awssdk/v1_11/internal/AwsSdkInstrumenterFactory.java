/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11.internal;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbExceptionEventExtractors.setDbClientExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingProcessExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingReceiveExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingSendExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingSettleExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.incubator.semconv.rpc.internal.RpcExceptionEventExtractors.setRpcClientExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import com.amazonaws.Request;
import com.amazonaws.Response;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingConsumerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingProcessMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingProducerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanKindExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingProcessContextCustomizer;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class AwsSdkInstrumenterFactory {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.aws-sdk-1.11";

  // messaging.operation.name values, named after the SQS API operations
  private static final String SEND_OPERATION_NAME = "send";
  private static final String CREATE_OPERATION_NAME = "create";
  private static final String RECEIVE_OPERATION_NAME = "receive";
  private static final String PROCESS_OPERATION_NAME = "process";
  private static final String DELETE_OPERATION_NAME = "delete";

  // copied from MessagingIncubatingAttributes
  private static final AttributeKey<String> MESSAGING_MESSAGE_ID =
      stringKey("messaging.message.id");

  private static final List<AttributesExtractor<Request<?>, Response<?>>>
      defaultAttributesExtractors = createAttributesExtractors(false);
  private static final List<AttributesExtractor<Request<?>, Response<?>>>
      extendedAttributesExtractors = createAttributesExtractors(true);

  private final OpenTelemetry openTelemetry;
  private final IncludeExclude headers;
  private final boolean captureExperimentalSpanAttributes;
  private final boolean messagingReceiveInstrumentationEnabled;
  private final boolean messageCreateSpansEnabled;

  public AwsSdkInstrumenterFactory(
      OpenTelemetry openTelemetry,
      IncludeExclude headers,
      boolean captureExperimentalSpanAttributes,
      boolean messagingReceiveInstrumentationEnabled,
      boolean messageCreateSpansEnabled) {
    this.openTelemetry = openTelemetry;
    this.headers = headers;
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    this.messagingReceiveInstrumentationEnabled = messagingReceiveInstrumentationEnabled;
    this.messageCreateSpansEnabled = messageCreateSpansEnabled;
  }

  private static List<AttributesExtractor<Request<?>, Response<?>>> createAttributesExtractors(
      boolean includeExperimental) {
    List<AttributesExtractor<Request<?>, Response<?>>> extractors =
        new ArrayList<>(
            asList(
                HttpClientAttributesExtractor.create(new AwsSdkHttpAttributesGetter()),
                RpcClientAttributesExtractor.create(new AwsSdkRpcAttributesGetter()),
                new SnsAttributesExtractor(),
                new AwsSdkAttributesExtractor()));
    if (includeExperimental) {
      extractors.add(new AwsSdkExperimentalAttributesExtractor());
    }
    return extractors;
  }

  public Instrumenter<Request<?>, Response<?>> requestInstrumenter() {
    return createInstrumenter(
        openTelemetry,
        new AwsSdkSpanNameExtractor(),
        SpanKindExtractor.alwaysClient(),
        attributesExtractors(),
        builder -> setRpcClientExceptionEventExtractor(builder),
        true);
  }

  private List<AttributesExtractor<Request<?>, Response<?>>> attributesExtractors() {
    return captureExperimentalSpanAttributes
        ? extendedAttributesExtractors
        : defaultAttributesExtractors;
  }

  private <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> messagingAttributesExtractor(
      MessagingAttributesGetter<REQUEST, RESPONSE> getter,
      MessagingOperationType operationType,
      String operationName) {
    return MessagingAttributesExtractor.builder(getter, operationType, operationName)
        .setHeaders(headers)
        .build();
  }

  public Instrumenter<SqsReceiveRequest, Response<?>> consumerReceiveInstrumenter() {
    MessagingOperationType operationType = MessagingOperationType.RECEIVE;
    SqsReceiveRequestAttributesGetter getter = new SqsReceiveRequestAttributesGetter();
    AttributesExtractor<SqsReceiveRequest, Response<?>> messagingAttributeExtractor =
        messagingAttributesExtractor(getter, operationType, RECEIVE_OPERATION_NAME);

    return createInstrumenter(
        openTelemetry,
        MessagingSpanNameExtractor.create(getter, operationType, RECEIVE_OPERATION_NAME),
        MessagingSpanKindExtractor.create(operationType),
        toSqsRequestExtractors(attributesExtractors()),
        singletonList(messagingAttributeExtractor),
        builder -> {
          builder.addOperationMetrics(MessagingConsumerMetrics.getForOperationType());
          setMessagingReceiveExceptionEventExtractor(builder);
          if (emitStableMessagingSemconv()) {
            builder.addSpanLinksExtractor(
                (spanLinks, parentContext, request) -> {
                  for (SqsMessage message : request.getMessages()) {
                    SpanContext spanContext =
                        Span.fromContext(message.getCreationContext()).getSpanContext();
                    if (spanContext.isValid()) {
                      spanLinks.addLink(spanContext, messageLinkAttributes(message));
                    }
                  }
                });
          }
        },
        messagingReceiveInstrumentationEnabled);
  }

  public Instrumenter<SqsProcessRequest, Response<?>> consumerProcessInstrumenter() {
    MessagingOperationType operationType = MessagingOperationType.PROCESS;
    SqsProcessRequestAttributesGetter getter = new SqsProcessRequestAttributesGetter();
    AttributesExtractor<SqsProcessRequest, Response<?>> messagingAttributeExtractor =
        messagingAttributesExtractor(getter, operationType, PROCESS_OPERATION_NAME);

    InstrumenterBuilder<SqsProcessRequest, Response<?>> builder =
        Instrumenter.<SqsProcessRequest, Response<?>>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(getter, operationType, PROCESS_OPERATION_NAME))
            .addAttributesExtractors(toSqsRequestExtractors(attributesExtractors()))
            .addAttributesExtractor(messagingAttributeExtractor)
            .addOperationMetrics(MessagingProcessMetrics.get());
    if (!messagingReceiveInstrumentationEnabled && emitStableMessagingSemconv()) {
      builder.addOperationMetrics(MessagingConsumerMetrics.getConsumedMessages());
    }
    setMessagingProcessExceptionEventExtractor(builder);

    if (emitStableMessagingSemconv() || messagingReceiveInstrumentationEnabled) {
      builder.addSpanLinksExtractor(
          (spanLinks, parentContext, request) -> {
            // getCreationContext() extracts against a root context, so it is either a valid
            // creation context or an invalid one; in particular it can never pick up the ambient
            // span. the creation context is linked even when it ends up being this span's parent,
            // which happens when there is no ambient span, because semconv asks for a link to the
            // creation context for every message the span accounts for
            //
            // the link carries no messaging.message.id: a process span accounts for a single
            // message, so that attribute belongs on the span itself, where it already is
            SpanContext creationSpanContext =
                Span.fromContext(request.getMessage().getCreationContext()).getSpanContext();
            if (creationSpanContext.isValid()) {
              spanLinks.addLink(creationSpanContext);
            }
          });
    }
    if (emitStableMessagingSemconv()) {
      builder.addContextCustomizer(
          MessagingProcessContextCustomizer.create(
              (parentContext, request) ->
                  parentContext.with(Span.fromContext(request.getMessage().getCreationContext()))));
    }
    return builder.buildInstrumenter(SpanKindExtractor.alwaysConsumer());
  }

  private static Attributes messageLinkAttributes(SqsMessage message) {
    return Attributes.of(MESSAGING_MESSAGE_ID, message.getMessageId());
  }

  private static List<AttributesExtractor<AbstractSqsRequest, Response<?>>> toSqsRequestExtractors(
      List<AttributesExtractor<Request<?>, Response<?>>> extractors) {
    List<AttributesExtractor<AbstractSqsRequest, Response<?>>> result = new ArrayList<>();
    for (AttributesExtractor<Request<?>, Response<?>> extractor : extractors) {
      result.add(
          new AttributesExtractor<AbstractSqsRequest, Response<?>>() {
            @Override
            public void onStart(
                AttributesBuilder attributes,
                Context parentContext,
                AbstractSqsRequest sqsRequest) {
              extractor.onStart(attributes, parentContext, sqsRequest.getRequest());
            }

            @Override
            public void onEnd(
                AttributesBuilder attributes,
                Context context,
                AbstractSqsRequest sqsRequest,
                @Nullable Response<?> response,
                @Nullable Throwable error) {
              extractor.onEnd(attributes, context, sqsRequest.getRequest(), response, error);
            }
          });
    }
    return result;
  }

  public Instrumenter<Request<?>, Response<?>> producerInstrumenter() {
    MessagingOperationType operationType = MessagingOperationType.SEND;
    SqsAttributesGetter getter = new SqsAttributesGetter();
    AttributesExtractor<Request<?>, Response<?>> messagingAttributeExtractor =
        messagingAttributesExtractor(getter, operationType, SEND_OPERATION_NAME);

    return createInstrumenter(
        openTelemetry,
        MessagingSpanNameExtractor.create(getter, operationType, SEND_OPERATION_NAME),
        request ->
            emitStableMessagingSemconv()
                    && messageCreateSpansEnabled
                    && SqsAccess.isBatchRequest(request)
                    && !SqsAccess.getBatchMessageContexts(request).isEmpty()
                ? SpanKind.CLIENT
                : SpanKind.PRODUCER,
        attributesExtractors(),
        singletonList(messagingAttributeExtractor),
        builder -> {
          builder.addOperationMetrics(MessagingProducerMetrics.getForOperationType());
          setMessagingSendExceptionEventExtractor(builder);
          if (emitStableMessagingSemconv() && messageCreateSpansEnabled) {
            builder.addSpanLinksExtractor(
                (spanLinks, parentContext, request) -> {
                  for (Context creationContext : SqsAccess.getBatchMessageContexts(request)) {
                    SpanContext spanContext = Span.fromContext(creationContext).getSpanContext();
                    if (spanContext.isValid()) {
                      spanLinks.addLink(spanContext);
                    }
                  }
                });
          }
        },
        true);
  }

  public Instrumenter<SqsCreateRequest, Void> producerCreateInstrumenter() {
    MessagingOperationType operationType = MessagingOperationType.CREATE;
    SqsCreateRequestAttributesGetter getter = new SqsCreateRequestAttributesGetter();
    return Instrumenter.<SqsCreateRequest, Void>builder(
            openTelemetry,
            INSTRUMENTATION_NAME,
            MessagingSpanNameExtractor.create(getter, operationType, CREATE_OPERATION_NAME))
        .addAttributesExtractor(
            messagingAttributesExtractor(getter, operationType, CREATE_OPERATION_NAME))
        .buildInstrumenter(MessagingSpanKindExtractor.create(operationType));
  }

  public Instrumenter<Request<?>, Response<?>> settleInstrumenter() {
    MessagingOperationType operationType = MessagingOperationType.SETTLE;
    SqsAttributesGetter getter = new SqsAttributesGetter();
    AttributesExtractor<Request<?>, Response<?>> messagingAttributeExtractor =
        messagingAttributesExtractor(getter, operationType, DELETE_OPERATION_NAME);

    return createInstrumenter(
        openTelemetry,
        MessagingSpanNameExtractor.create(getter, operationType, DELETE_OPERATION_NAME),
        MessagingSpanKindExtractor.create(operationType),
        attributesExtractors(),
        singletonList(messagingAttributeExtractor),
        builder -> {
          builder.addOperationMetrics(MessagingConsumerMetrics.getForOperationType());
          setMessagingSettleExceptionEventExtractor(builder);
        },
        true);
  }

  public Instrumenter<Request<?>, Response<?>> dynamoDbInstrumenter() {
    return createInstrumenter(
        openTelemetry,
        new AwsSdkSpanNameExtractor(),
        SpanKindExtractor.alwaysClient(),
        attributesExtractors(),
        builder -> {
          builder
              .addAttributesExtractor(new DynamoDbAttributesExtractor())
              .addOperationMetrics(DbClientMetrics.get());
          setDbClientExceptionEventExtractor(builder);
        },
        true);
  }

  private static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> createInstrumenter(
      OpenTelemetry openTelemetry,
      SpanNameExtractor<REQUEST> spanNameExtractor,
      SpanKindExtractor<REQUEST> spanKindExtractor,
      List<? extends AttributesExtractor<? super REQUEST, ? super RESPONSE>> attributeExtractors,
      List<AttributesExtractor<REQUEST, RESPONSE>> additionalAttributeExtractors,
      Consumer<InstrumenterBuilder<REQUEST, RESPONSE>> exceptionEventCustomizer,
      boolean enabled) {
    return createInstrumenter(
        openTelemetry,
        spanNameExtractor,
        spanKindExtractor,
        attributeExtractors,
        builder -> {
          builder.addAttributesExtractors(additionalAttributeExtractors);
          exceptionEventCustomizer.accept(builder);
        },
        enabled);
  }

  private static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> createInstrumenter(
      OpenTelemetry openTelemetry,
      SpanNameExtractor<REQUEST> spanNameExtractor,
      SpanKindExtractor<REQUEST> spanKindExtractor,
      List<? extends AttributesExtractor<? super REQUEST, ? super RESPONSE>> attributeExtractors,
      Consumer<InstrumenterBuilder<REQUEST, RESPONSE>> customizer,
      boolean enabled) {
    InstrumenterBuilder<REQUEST, RESPONSE> builder =
        Instrumenter.<REQUEST, RESPONSE>builder(
                openTelemetry, INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractors(attributeExtractors)
            .setEnabled(enabled);
    customizer.accept(builder);

    return builder.buildInstrumenter(spanKindExtractor);
  }
}
