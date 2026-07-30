/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

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

  private static SqsMessage messageWithTraceHeader() {
    return new SqsMessage() {
      @Override
      public Context getCreationContext() {
        return Context.root();
      }

      @Override
      public Map<String, MessageAttributeValue> messageAttributes() {
        return emptyMap();
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
      public String getMessageId() {
        return "";
      }
    };
  }
}
