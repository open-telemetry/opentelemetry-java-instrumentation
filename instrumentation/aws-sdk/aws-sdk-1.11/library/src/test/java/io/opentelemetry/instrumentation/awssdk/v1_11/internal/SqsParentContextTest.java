/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import org.junit.jupiter.api.Test;

class SqsParentContextTest {

  private static final ContextKey<String> TEST_CONTEXT_KEY = ContextKey.named("test-context-key");
  private static final String TRACE_HEADER =
      "Root=1-5759e988-bd862e3fe1be46a994272793;Parent=53995c3f42cd8ad8;Sampled=1";

  @Test
  void preservesParentContextValues() {
    Context parentContext = Context.root().with(TEST_CONTEXT_KEY, "test-value");

    Context extractedContext =
        SqsParentContext.ofSystemAttributes(
            parentContext, singletonMap(SqsParentContext.AWS_TRACE_SYSTEM_ATTRIBUTE, TRACE_HEADER));

    assertThat(extractedContext.get(TEST_CONTEXT_KEY)).isEqualTo("test-value");
    assertThat(Span.fromContext(extractedContext).getSpanContext().isValid()).isTrue();
  }

  @Test
  void receivesCreationContextWhenCreateSpanEmissionIsDisabled() {
    assumeTrue(emitStableMessagingSemconv());
    ReceiveMessageRequest request = new ReceiveMessageRequest("https://example.com/queue");

    SqsImpl.beforeMarshalling(
        request, /* producerCreateInstrumenter= */ null, /* messageCreateSpansEnabled= */ false);

    assertThat(request.getMessageAttributeNames())
        .contains(SqsParentContext.AWS_TRACE_MESSAGE_ATTRIBUTE);

    Message message =
        new Message()
            .addMessageAttributesEntry(
                SqsParentContext.AWS_TRACE_MESSAGE_ATTRIBUTE,
                new MessageAttributeValue().withDataType("String").withStringValue(TRACE_HEADER));
    Context creationContext = SqsMessageImpl.wrap(message).getCreationContext();
    assertThat(Span.fromContext(creationContext).getSpanContext().getSpanId())
        .isEqualTo("53995c3f42cd8ad8");
  }
}
