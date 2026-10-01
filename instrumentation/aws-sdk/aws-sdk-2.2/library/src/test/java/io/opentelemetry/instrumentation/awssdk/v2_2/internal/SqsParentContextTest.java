/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;

class SqsParentContextTest {

  private static final ContextKey<String> TEST_CONTEXT_KEY = ContextKey.named("test-context-key");
  private static final String TRACE_HEADER =
      "Root=1-5759e988-bd862e3fe1be46a994272793;Parent=53995c3f42cd8ad8;Sampled=1";

  @Test
  void preservesParentContextValues() {
    Context parentContext = Context.root().with(TEST_CONTEXT_KEY, "test-value");

    Context extractedContext =
        SqsParentContext.ofMessage(parentContext, messageWithTraceHeader(), null, true);

    assertThat(extractedContext.get(TEST_CONTEXT_KEY)).isEqualTo("test-value");
    assertThat(Span.fromContext(extractedContext).getSpanContext().isValid()).isTrue();
  }

  @Test
  void preservesNonSpanExtractedValuesWhenUsingXrayFallback() {
    TextMapPropagator propagator =
        new TextMapPropagator() {
          @Override
          public <C> void inject(Context context, C carrier, TextMapSetter<C> setter) {}

          @Override
          public <C> Context extract(Context context, C carrier, TextMapGetter<C> getter) {
            return context.with(TEST_CONTEXT_KEY, "extracted-value");
          }

          @Override
          public List<String> fields() {
            return emptyList();
          }
        };

    Context extractedContext =
        SqsParentContext.ofMessage(Context.root(), messageWithTraceHeader(), propagator, true);

    assertThat(extractedContext.get(TEST_CONTEXT_KEY)).isEqualTo("extracted-value");
    assertThat(Span.fromContext(extractedContext).getSpanContext().isValid()).isTrue();
  }

  @Test
  void usesXrayFallbackWhenOnlyAmbientSpanIsPresent() {
    SpanContext ambient =
        SpanContext.create(
            "11111111111111111111111111111111",
            "1111111111111111",
            TraceFlags.getSampled(),
            TraceState.getDefault());
    Context parentContext = Context.root().with(Span.wrap(ambient));

    Context extractedContext =
        SqsParentContext.ofMessage(
            parentContext,
            messageWithTraceHeader(),
            W3CTraceContextPropagator.getInstance(),
            /* shouldUseXrayPropagator= */ true);

    SpanContext extracted = Span.fromContext(extractedContext).getSpanContext();
    assertThat(extracted.isValid()).isTrue();
    assertThat(extracted.getTraceId()).isNotEqualTo(ambient.getTraceId());
  }

  @Test
  void usesMessagingPropagatorWhenItExtractsTheAmbientSpan() {
    SpanContext ambient =
        SpanContext.create(
            "11111111111111111111111111111111",
            "1111111111111111",
            TraceFlags.getSampled(),
            TraceState.getDefault());
    Context parentContext = Context.root().with(Span.wrap(ambient));

    // the message carries the ambient span as its creation context, which can happen when the
    // message is produced and consumed inside the same span
    Context extractedContext =
        SqsParentContext.ofMessage(
            parentContext,
            messageWithTraceHeader(traceParent(ambient)),
            W3CTraceContextPropagator.getInstance(),
            /* shouldUseXrayPropagator= */ true);

    // the X-Ray fallback must not replace the creation context extracted from the message
    SpanContext extracted = Span.fromContext(extractedContext).getSpanContext();
    assertThat(extracted.getTraceId()).isEqualTo(ambient.getTraceId());
    assertThat(extracted.getSpanId()).isEqualTo(ambient.getSpanId());
  }

  @Test
  void readsAndWritesMessageSystemAttribute() {
    assumeTrue(SqsMessageSystemAttributeAccess.isAvailable());

    SendMessageBatchRequestEntry entry =
        SendMessageBatchRequestEntry.builder().id("id").messageBody("body").build();
    assertThat(SqsMessageSystemAttributeAccess.canSetTraceHeader(entry)).isTrue();
    SendMessageBatchRequestEntry updatedEntry =
        SqsMessageSystemAttributeAccess.withTraceHeader(entry, TRACE_HEADER);
    assertThat(updatedEntry).isNotNull();
    assertThat(SqsMessageSystemAttributeAccess.canSetTraceHeader(updatedEntry)).isFalse();
    assertThat(SqsMessageSystemAttributeAccess.getTraceHeader(updatedEntry))
        .isEqualTo(TRACE_HEADER);
    assertThat(SqsMessageSystemAttributeAccess.withTraceHeader(updatedEntry, "replacement"))
        .isNull();
    Context creationContext =
        SqsParentContext.ofTraceHeader(
            SqsMessageSystemAttributeAccess.getTraceHeader(updatedEntry));
    assertThat(Span.fromContext(creationContext).getSpanContext().getSpanId())
        .isEqualTo("53995c3f42cd8ad8");
  }

  private static String traceParent(SpanContext spanContext) {
    return "00-"
        + spanContext.getTraceId()
        + "-"
        + spanContext.getSpanId()
        + "-"
        + spanContext.getTraceFlags().asHex();
  }

  private static SqsMessage messageWithTraceHeader() {
    return messageWithTraceHeader(null);
  }

  private static SqsMessage messageWithTraceHeader(@Nullable String traceParent) {
    Map<String, MessageAttributeValue> messageAttributes =
        traceParent == null
            ? emptyMap()
            : singletonMap(
                "traceparent",
                MessageAttributeValue.builder()
                    .dataType("String")
                    .stringValue(traceParent)
                    .build());
    return new SqsMessage() {
      @Override
      public Context getCreationContext() {
        return Context.root();
      }

      @Override
      public Map<String, MessageAttributeValue> messageAttributes() {
        return messageAttributes;
      }

      @Override
      public Map<String, String> attributesAsStrings() {
        return singletonMap(SqsParentContext.AWS_TRACE_SYSTEM_ATTRIBUTE, TRACE_HEADER);
      }

      @Override
      public String getMessageAttribute(String name) {
        return "";
      }

      @Override
      public Collection<String> getMessageAttributeNames() {
        return messageAttributes.keySet();
      }

      @Override
      public String getMessageId() {
        return "";
      }
    };
  }
}
