/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_FINISH_REASONS;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_ID;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_MODEL;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_SYSTEM;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_TOKEN_TYPE;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_INPUT_TOKENS;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_OUTPUT_TOKENS;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues.CHAT;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiSystemIncubatingValues.OPENAI;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiTokenTypeIncubatingValues.COMPLETION;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiTokenTypeIncubatingValues.INPUT;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import io.opentelemetry.api.common.KeyValue;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ChatTest extends AbstractChatTest {

  @RegisterExtension
  private static final LibraryInstrumentationExtension testing =
      LibraryInstrumentationExtension.create();

  private static OpenAITelemetry telemetry;

  @BeforeAll
  static void setup() {
    telemetry =
        OpenAITelemetry.builder(testing.getOpenTelemetry()).setCaptureMessageContent(true).build();
  }

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }

  @Override
  protected OpenAIClient wrap(OpenAIClient client) {
    return telemetry.wrap(client);
  }

  @Override
  // OpenAI SDK does not expose OkHttp client in a way we can wrap.
  protected final List<Consumer<SpanDataAssert>> maybeWithTransportSpan(
      Consumer<SpanDataAssert> span) {
    return singletonList(span);
  }

  @Test
  void basicNoCaptureContent() {
    OpenAIClient client =
        OpenAITelemetry.builder(testing.getOpenTelemetry()).build().wrap(getRawClient());

    ChatCompletionCreateParams params =
        ChatCompletionCreateParams.builder()
            .messages(singletonList(createUserMessage(TEST_CHAT_INPUT)))
            .model(TEST_CHAT_MODEL)
            .build();

    ChatCompletion response = client.chat().completions().create(params);
    String content = "Atlantic Ocean";
    assertThat(response.choices().get(0).message().content()).hasValue(content);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasAttributesSatisfyingExactly(
                            equalTo(GEN_AI_SYSTEM, OPENAI),
                            equalTo(GEN_AI_OPERATION_NAME, CHAT),
                            equalTo(GEN_AI_REQUEST_MODEL, TEST_CHAT_MODEL),
                            satisfies(GEN_AI_RESPONSE_ID, id -> id.startsWith("chatcmpl-")),
                            equalTo(GEN_AI_RESPONSE_MODEL, TEST_CHAT_RESPONSE_MODEL),
                            satisfies(
                                GEN_AI_RESPONSE_FINISH_REASONS,
                                reasons -> reasons.containsExactly("stop")),
                            equalTo(GEN_AI_USAGE_INPUT_TOKENS, 22L),
                            equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 2L))));

    getTesting()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            metric ->
                metric
                    .hasName("gen_ai.client.operation.duration")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSumGreaterThan(0.0)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(GEN_AI_SYSTEM, "openai"),
                                            equalTo(GEN_AI_OPERATION_NAME, "chat"),
                                            equalTo(GEN_AI_REQUEST_MODEL, TEST_CHAT_MODEL),
                                            equalTo(
                                                GEN_AI_RESPONSE_MODEL, TEST_CHAT_RESPONSE_MODEL)))),
            metric ->
                metric
                    .hasName("gen_ai.client.token.usage")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(22.0)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(GEN_AI_SYSTEM, "openai"),
                                            equalTo(GEN_AI_OPERATION_NAME, "chat"),
                                            equalTo(GEN_AI_REQUEST_MODEL, TEST_CHAT_MODEL),
                                            equalTo(
                                                GEN_AI_RESPONSE_MODEL, TEST_CHAT_RESPONSE_MODEL),
                                            equalTo(GEN_AI_TOKEN_TYPE, INPUT)),
                                point ->
                                    point
                                        .hasSum(2.0)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(GEN_AI_SYSTEM, "openai"),
                                            equalTo(GEN_AI_OPERATION_NAME, "chat"),
                                            equalTo(GEN_AI_REQUEST_MODEL, TEST_CHAT_MODEL),
                                            equalTo(
                                                GEN_AI_RESPONSE_MODEL, TEST_CHAT_RESPONSE_MODEL),
                                            equalTo(GEN_AI_TOKEN_TYPE, COMPLETION)))));

    SpanContext spanCtx = getTesting().waitForTraces(1).get(0).get(0).getSpanContext();

    getTesting()
        .waitAndAssertLogRecords(
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, OPENAI), equalTo(EVENT_NAME, "gen_ai.user.message"))
                    .hasSpanContext(spanCtx)
                    .hasBody(Value.of(emptyMap())),
            log -> {
              log.hasAttributesSatisfyingExactly(
                      equalTo(GEN_AI_SYSTEM, OPENAI), equalTo(EVENT_NAME, "gen_ai.choice"))
                  .hasSpanContext(spanCtx)
                  .hasBody(
                      Value.of(
                          KeyValue.of("finish_reason", Value.of("stop")),
                          KeyValue.of("index", Value.of(0)),
                          KeyValue.of("message", Value.of(emptyMap()))));
            });
  }

  @Test
  void multipleChoicesNoCaptureContent() {
    OpenAIClient client =
        OpenAITelemetry.builder(testing.getOpenTelemetry()).build().wrap(getRawClient());

    ChatCompletionCreateParams params =
        ChatCompletionCreateParams.builder()
            .messages(Collections.singletonList(createUserMessage(TEST_CHAT_INPUT)))
            .model(TEST_CHAT_MODEL)
            .n(2)
            .build();

    ChatCompletion response = client.chat().completions().create(params);
    String content1 = "South Atlantic Ocean.";
    assertThat(response.choices().get(0).message().content()).hasValue(content1);
    String content2 = "Atlantic Ocean.";
    assertThat(response.choices().get(1).message().content()).hasValue(content2);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasAttributesSatisfyingExactly(
                            equalTo(GEN_AI_SYSTEM, OPENAI),
                            equalTo(GEN_AI_OPERATION_NAME, CHAT),
                            equalTo(GEN_AI_REQUEST_MODEL, TEST_CHAT_MODEL),
                            satisfies(GEN_AI_RESPONSE_ID, id -> id.startsWith("chatcmpl-")),
                            equalTo(GEN_AI_RESPONSE_MODEL, TEST_CHAT_RESPONSE_MODEL),
                            satisfies(
                                GEN_AI_RESPONSE_FINISH_REASONS,
                                reasons -> reasons.containsExactly("stop", "stop")),
                            equalTo(GEN_AI_USAGE_INPUT_TOKENS, 22L),
                            equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 7L))));

    getTesting()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            metric ->
                metric
                    .hasName("gen_ai.client.operation.duration")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSumGreaterThan(0.0)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(GEN_AI_SYSTEM, "openai"),
                                            equalTo(GEN_AI_OPERATION_NAME, "chat"),
                                            equalTo(GEN_AI_REQUEST_MODEL, TEST_CHAT_MODEL),
                                            equalTo(
                                                GEN_AI_RESPONSE_MODEL, TEST_CHAT_RESPONSE_MODEL)))),
            metric ->
                metric
                    .hasName("gen_ai.client.token.usage")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(22.0)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(GEN_AI_SYSTEM, "openai"),
                                            equalTo(GEN_AI_OPERATION_NAME, "chat"),
                                            equalTo(GEN_AI_REQUEST_MODEL, TEST_CHAT_MODEL),
                                            equalTo(
                                                GEN_AI_RESPONSE_MODEL, TEST_CHAT_RESPONSE_MODEL),
                                            equalTo(GEN_AI_TOKEN_TYPE, INPUT)),
                                point ->
                                    point
                                        .hasSum(7.0)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(GEN_AI_SYSTEM, "openai"),
                                            equalTo(GEN_AI_OPERATION_NAME, "chat"),
                                            equalTo(GEN_AI_REQUEST_MODEL, TEST_CHAT_MODEL),
                                            equalTo(
                                                GEN_AI_RESPONSE_MODEL, TEST_CHAT_RESPONSE_MODEL),
                                            equalTo(GEN_AI_TOKEN_TYPE, COMPLETION)))));

    SpanContext spanCtx = getTesting().waitForTraces(1).get(0).get(0).getSpanContext();

    getTesting()
        .waitAndAssertLogRecords(
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, OPENAI), equalTo(EVENT_NAME, "gen_ai.user.message"))
                    .hasSpanContext(spanCtx)
                    .hasBody(Value.of(emptyMap())),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, OPENAI), equalTo(EVENT_NAME, "gen_ai.choice"))
                    .hasSpanContext(spanCtx)
                    .hasBody(
                        Value.of(
                            KeyValue.of("finish_reason", Value.of("stop")),
                            KeyValue.of("index", Value.of(0)),
                            KeyValue.of("message", Value.of(emptyMap())))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, OPENAI), equalTo(EVENT_NAME, "gen_ai.choice"))
                    .hasSpanContext(spanCtx)
                    .hasBody(
                        Value.of(
                            KeyValue.of("finish_reason", Value.of("stop")),
                            KeyValue.of("index", Value.of(1)),
                            KeyValue.of("message", Value.of(emptyMap())))));
  }

  @Test
  void toolCallsNoCaptureContent() {
    OpenAIClient client =
        OpenAITelemetry.builder(testing.getOpenTelemetry()).build().wrap(getRawClient());

    List<ChatCompletionMessageParam> chatMessages = new ArrayList<>();
    chatMessages.add(createSystemMessage("You are a helpful assistant providing weather updates."));
    chatMessages.add(createUserMessage("What is the weather in New York City and London?"));

    ChatCompletionCreateParams params =
        ChatCompletionCreateParams.builder()
            .messages(chatMessages)
            .model(TEST_CHAT_MODEL)
            .addTool(buildGetWeatherToolDefinition())
            .build();

    ChatCompletion response = client.chat().completions().create(params);

    assertThat(response.choices().get(0).message().content()).isEmpty();

    List<ChatCompletionMessageToolCall> toolCalls =
        response.choices().get(0).message().toolCalls().get();
    assertThat(toolCalls).hasSize(2);
    String newYorkCallId =
        toolCalls.stream()
            .filter(call -> call.function().arguments().contains("New York"))
            .map(ChatCompletionMessageToolCall::id)
            .findFirst()
            .get();
    String londonCallId =
        toolCalls.stream()
            .filter(call -> call.function().arguments().contains("London"))
            .map(ChatCompletionMessageToolCall::id)
            .findFirst()
            .get();

    assertThat(newYorkCallId).startsWith("call_");
    assertThat(londonCallId).startsWith("call_");

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasAttributesSatisfyingExactly(
                            equalTo(GEN_AI_SYSTEM, OPENAI),
                            equalTo(GEN_AI_OPERATION_NAME, CHAT),
                            equalTo(GEN_AI_REQUEST_MODEL, TEST_CHAT_MODEL),
                            satisfies(GEN_AI_RESPONSE_ID, id -> id.startsWith("chatcmpl-")),
                            equalTo(GEN_AI_RESPONSE_MODEL, TEST_CHAT_RESPONSE_MODEL),
                            satisfies(
                                GEN_AI_RESPONSE_FINISH_REASONS,
                                reasons -> reasons.containsExactly("tool_calls")),
                            equalTo(GEN_AI_USAGE_INPUT_TOKENS, 67L),
                            equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 46L))));

    getTesting()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            metric ->
                metric
                    .hasName("gen_ai.client.operation.duration")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSumGreaterThan(0.0)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(GEN_AI_SYSTEM, "openai"),
                                            equalTo(GEN_AI_OPERATION_NAME, "chat"),
                                            equalTo(GEN_AI_REQUEST_MODEL, TEST_CHAT_MODEL),
                                            equalTo(
                                                GEN_AI_RESPONSE_MODEL, TEST_CHAT_RESPONSE_MODEL)))),
            metric ->
                metric
                    .hasName("gen_ai.client.token.usage")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(67)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(GEN_AI_SYSTEM, "openai"),
                                            equalTo(GEN_AI_OPERATION_NAME, "chat"),
                                            equalTo(GEN_AI_REQUEST_MODEL, TEST_CHAT_MODEL),
                                            equalTo(
                                                GEN_AI_RESPONSE_MODEL, TEST_CHAT_RESPONSE_MODEL),
                                            equalTo(GEN_AI_TOKEN_TYPE, INPUT)),
                                point ->
                                    point
                                        .hasSum(46.0)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(GEN_AI_SYSTEM, "openai"),
                                            equalTo(GEN_AI_OPERATION_NAME, "chat"),
                                            equalTo(GEN_AI_REQUEST_MODEL, TEST_CHAT_MODEL),
                                            equalTo(
                                                GEN_AI_RESPONSE_MODEL, TEST_CHAT_RESPONSE_MODEL),
                                            equalTo(GEN_AI_TOKEN_TYPE, COMPLETION)))));

    SpanContext spanCtx = getTesting().waitForTraces(1).get(0).get(0).getSpanContext();

    getTesting()
        .waitAndAssertLogRecords(
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, OPENAI),
                        equalTo(EVENT_NAME, "gen_ai.system.message"))
                    .hasSpanContext(spanCtx)
                    .hasBody(Value.of(emptyMap())),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, OPENAI), equalTo(EVENT_NAME, "gen_ai.user.message"))
                    .hasSpanContext(spanCtx)
                    .hasBody(Value.of(emptyMap())),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, OPENAI), equalTo(EVENT_NAME, "gen_ai.choice"))
                    .hasSpanContext(spanCtx)
                    .hasBody(
                        Value.of(
                            KeyValue.of("finish_reason", Value.of("tool_calls")),
                            KeyValue.of("index", Value.of(0)),
                            KeyValue.of(
                                "message",
                                Value.of(
                                    KeyValue.of(
                                        "tool_calls",
                                        Value.of(
                                            Value.of(
                                                KeyValue.of(
                                                    "function",
                                                    Value.of(
                                                        KeyValue.of(
                                                            "name", Value.of("get_weather")))),
                                                KeyValue.of("id", Value.of(newYorkCallId)),
                                                KeyValue.of("type", Value.of("function"))),
                                            Value.of(
                                                KeyValue.of(
                                                    "function",
                                                    Value.of(
                                                        KeyValue.of(
                                                            "name", Value.of("get_weather")))),
                                                KeyValue.of("id", Value.of(londonCallId)),
                                                KeyValue.of("type", Value.of("function"))))))))));

    getTesting().clearData();

    ChatCompletionMessageParam assistantMessage = createAssistantMessage(toolCalls);

    chatMessages.add(assistantMessage);
    chatMessages.add(createToolMessage("25 degrees and sunny", newYorkCallId));
    chatMessages.add(createToolMessage("15 degrees and raining", londonCallId));

    client
        .chat()
        .completions()
        .create(
            ChatCompletionCreateParams.builder()
                .messages(chatMessages)
                .model(TEST_CHAT_MODEL)
                .build());

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasAttributesSatisfyingExactly(
                            equalTo(GEN_AI_SYSTEM, OPENAI),
                            equalTo(GEN_AI_OPERATION_NAME, CHAT),
                            equalTo(GEN_AI_REQUEST_MODEL, TEST_CHAT_MODEL),
                            satisfies(GEN_AI_RESPONSE_ID, id -> id.startsWith("chatcmpl-")),
                            equalTo(GEN_AI_RESPONSE_MODEL, TEST_CHAT_RESPONSE_MODEL),
                            satisfies(
                                GEN_AI_RESPONSE_FINISH_REASONS,
                                reasons -> reasons.containsExactly("stop")),
                            equalTo(GEN_AI_USAGE_INPUT_TOKENS, 99L),
                            equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 25L))));

    getTesting()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            metric ->
                metric
                    .hasName("gen_ai.client.operation.duration")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSumGreaterThan(0.0)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(GEN_AI_SYSTEM, "openai"),
                                            equalTo(GEN_AI_OPERATION_NAME, "chat"),
                                            equalTo(GEN_AI_REQUEST_MODEL, TEST_CHAT_MODEL),
                                            equalTo(
                                                GEN_AI_RESPONSE_MODEL, TEST_CHAT_RESPONSE_MODEL)))),
            metric ->
                metric
                    .hasName("gen_ai.client.token.usage")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(99)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(GEN_AI_SYSTEM, "openai"),
                                            equalTo(GEN_AI_OPERATION_NAME, "chat"),
                                            equalTo(GEN_AI_REQUEST_MODEL, TEST_CHAT_MODEL),
                                            equalTo(
                                                GEN_AI_RESPONSE_MODEL, TEST_CHAT_RESPONSE_MODEL),
                                            equalTo(GEN_AI_TOKEN_TYPE, INPUT)),
                                point ->
                                    point
                                        .hasSum(25.0)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(GEN_AI_SYSTEM, "openai"),
                                            equalTo(GEN_AI_OPERATION_NAME, "chat"),
                                            equalTo(GEN_AI_REQUEST_MODEL, TEST_CHAT_MODEL),
                                            equalTo(
                                                GEN_AI_RESPONSE_MODEL, TEST_CHAT_RESPONSE_MODEL),
                                            equalTo(GEN_AI_TOKEN_TYPE, COMPLETION)))));

    SpanContext spanCtx1 = getTesting().waitForTraces(1).get(0).get(0).getSpanContext();

    getTesting()
        .waitAndAssertLogRecords(
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, OPENAI),
                        equalTo(EVENT_NAME, "gen_ai.system.message"))
                    .hasSpanContext(spanCtx1)
                    .hasBody(Value.of(emptyMap())),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, OPENAI), equalTo(EVENT_NAME, "gen_ai.user.message"))
                    .hasSpanContext(spanCtx1)
                    .hasBody(Value.of(emptyMap())),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, OPENAI),
                        equalTo(EVENT_NAME, "gen_ai.assistant.message"))
                    .hasSpanContext(spanCtx1)
                    .hasBody(
                        Value.of(
                            KeyValue.of(
                                "tool_calls",
                                Value.of(
                                    Value.of(
                                        KeyValue.of(
                                            "function",
                                            Value.of(KeyValue.of("name", Value.of("get_weather")))),
                                        KeyValue.of("id", Value.of(newYorkCallId)),
                                        KeyValue.of("type", Value.of("function"))),
                                    Value.of(
                                        KeyValue.of(
                                            "function",
                                            Value.of(KeyValue.of("name", Value.of("get_weather")))),
                                        KeyValue.of("id", Value.of(londonCallId)),
                                        KeyValue.of("type", Value.of("function"))))))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, OPENAI), equalTo(EVENT_NAME, "gen_ai.tool.message"))
                    .hasSpanContext(spanCtx1)
                    .hasBody(Value.of(KeyValue.of("id", Value.of(newYorkCallId)))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, OPENAI), equalTo(EVENT_NAME, "gen_ai.tool.message"))
                    .hasSpanContext(spanCtx1)
                    .hasBody(Value.of(KeyValue.of("id", Value.of(londonCallId)))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, OPENAI), equalTo(EVENT_NAME, "gen_ai.choice"))
                    .hasSpanContext(spanCtx1)
                    .hasBody(
                        Value.of(
                            KeyValue.of("finish_reason", Value.of("stop")),
                            KeyValue.of("index", Value.of(0)),
                            KeyValue.of("message", Value.of(emptyMap())))));
  }
}
