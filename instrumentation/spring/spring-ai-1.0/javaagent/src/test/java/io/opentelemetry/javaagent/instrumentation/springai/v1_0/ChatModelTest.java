/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springai.v1_0;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_PROVIDER_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_FINISH_REASONS;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_ID;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_MODEL;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_INPUT_TOKENS;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_OUTPUT_TOKENS;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues.CHAT;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.KeyValue;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.springai.v1_0.app.TestChatModel;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.DefaultChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

@SuppressWarnings({"OtelDeprecatedApiUsage", "PublicApiNamedStreamShouldReturnStream"})
class ChatModelTest {

  private static final String MODEL = "test-model";
  private static final boolean CAPTURE_MESSAGE_CONTENT_AS_SPAN_ATTRIBUTES =
      Boolean.getBoolean(
          "otel.instrumentation.spring-ai.experimental.capture-message-content-as-span-attributes.enabled");

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private final ChatModel chatModel = new TestChatModel();

  @Test
  void call() {
    testing.runWithSpan("parent", () -> chatModel.call(prompt()));

    SpanContext spanContext = testing.waitForTraces(1).get(0).get(1).getSpanContext();
    assertTraces();
    assertMessageEvents(spanContext);
  }

  @Test
  void stream() {
    testing.runWithSpan("parent", () -> chatModel.stream(prompt()).blockLast());

    SpanContext spanContext = testing.waitForTraces(1).get(0).get(1).getSpanContext();
    assertTraces();
    assertMessageEvents(spanContext);
  }

  @Test
  void messageSpanAttributeIsTruncatedWithoutChangingItsJsonStructure() {
    String content = repeatedContent(8193);
    testing.runWithSpan("parent", () -> chatModel.call(new Prompt(content)));

    assertThat(
            testing
                .waitForTraces(1)
                .get(0)
                .get(1)
                .getAttributes()
                .get(stringKey("gen_ai.input.messages")))
        .isEqualTo(
            messageSpanAttribute(
                "[{\"role\":\"user\",\"content\":\"" + repeatedContent(8192) + "\"}]"));
    assertThat(
            testing
                .waitForTraces(1)
                .get(0)
                .get(1)
                .getAttributes()
                .get(booleanKey("gen_ai.input.messages.truncated")))
        .isEqualTo(messageSpanAttribute(true));
  }

  private static void assertTraces() {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
                span ->
                    span.hasName("chat " + MODEL)
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(GEN_AI_PROVIDER_NAME, "test"),
                            equalTo(GEN_AI_OPERATION_NAME, CHAT),
                            equalTo(GEN_AI_REQUEST_MODEL, MODEL),
                            equalTo(GEN_AI_RESPONSE_FINISH_REASONS, singletonList("stop")),
                            equalTo(GEN_AI_RESPONSE_ID, "response-id"),
                            equalTo(GEN_AI_RESPONSE_MODEL, MODEL),
                            equalTo(GEN_AI_USAGE_INPUT_TOKENS, 3L),
                            equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 2L),
                            equalTo(
                                stringKey("gen_ai.input.messages"),
                                messageSpanAttribute(
                                    "[{\"role\":\"user\",\"content\":\"Tell me about traces\"}]")),
                            equalTo(
                                stringKey("gen_ai.output.messages"),
                                messageSpanAttribute(
                                    "[{\"role\":\"assistant\",\"content\":\"A trace represents an end-to-end request.\"}]")))));
  }

  private static void assertMessageEvents(SpanContext spanContext) {
    testing.waitAndAssertLogRecords(
        log ->
            log.hasAttributesSatisfyingExactly(
                    equalTo(GEN_AI_PROVIDER_NAME, "test"),
                    equalTo(stringKey("event.name"), "gen_ai.user.message"))
                .hasSpanContext(spanContext)
                .hasBody(Value.of(KeyValue.of("content", Value.of("Tell me about traces")))),
        log ->
            log.hasAttributesSatisfyingExactly(
                    equalTo(GEN_AI_PROVIDER_NAME, "test"),
                    equalTo(stringKey("event.name"), "gen_ai.choice"))
                .hasSpanContext(spanContext)
                .hasBody(
                    Value.of(
                        KeyValue.of("finish_reason", Value.of("stop")),
                        KeyValue.of("index", Value.of(0)),
                        KeyValue.of(
                            "message",
                            Value.of(
                                KeyValue.of(
                                    "content",
                                    Value.of("A trace represents an end-to-end request.")))))));
  }

  private static Prompt prompt() {
    DefaultChatOptions options = new DefaultChatOptions();
    options.setModel(MODEL);
    return new Prompt("Tell me about traces", options);
  }

  private static String repeatedContent(int length) {
    StringBuilder content = new StringBuilder(length);
    for (int index = 0; index < length; index++) {
      content.append('a');
    }
    return content.toString();
  }

  @Nullable
  private static <T> T messageSpanAttribute(T value) {
    return CAPTURE_MESSAGE_CONTENT_AS_SPAN_ATTRIBUTES ? value : null;
  }
}
