/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springai.v1_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_PROVIDER_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_FREQUENCY_PENALTY;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MAX_TOKENS;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_PRESENCE_PENALTY;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_STOP_SEQUENCES;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_TEMPERATURE;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_TOP_K;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_TOP_P;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_FINISH_REASONS;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_ID;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_MODEL;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_TOKEN_TYPE;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_INPUT_TOKENS;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_OUTPUT_TOKENS;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues.CHAT;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiTokenTypeIncubatingValues.INPUT;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiTokenTypeIncubatingValues.OUTPUT;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.common.KeyValue;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.springai.v1_0.app.TestChatModel;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.DefaultChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

@SuppressWarnings("OtelDeprecatedApiUsage")
class ChatModelTest {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-ai-1.0";
  private static final String MODEL = "test-model";
  private static final String PROMPT = "Tell me about traces";
  private static final String RESPONSE = "A trace represents an end-to-end request.";
  private static final boolean CAPTURE_MESSAGE_CONTENT =
      Boolean.getBoolean("otel.instrumentation.genai.capture-message-content");
  private static final boolean EXPERIMENTAL_ATTRIBUTES =
      Boolean.getBoolean(
          "otel.instrumentation.spring-ai.experimental.capture-message-content-as-span-attributes.enabled");

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private final TestChatModel chatModel = new TestChatModel(defaultOptions());

  @Test
  void callUsesDefaultOptionsAndCapturesRequestFields() {
    testing.runWithSpan("parent", () -> chatModel.call(prompt()));

    SpanContext spanContext = testing.waitForTraces(1).get(0).get(1).getSpanContext();
    assertCurrentSpanContext(chatModel.getLastSpanContext(), spanContext);
    assertTraces("test");
    assertMetrics();
    assertMessageEvents(spanContext);
  }

  @SuppressWarnings("PublicApiNamedStreamShouldReturnStream")
  @Test
  void stream() {
    testing.runWithSpan("parent", () -> chatModel.stream(prompt()).blockLast());

    SpanContext spanContext = testing.waitForTraces(1).get(0).get(1).getSpanContext();
    assertCurrentSpanContext(chatModel.getLastSpanContext(), spanContext);
    assertTraces("test");
    assertMessageEvents(spanContext);
  }

  @Test
  void streamUsesParentFromReactorContext() {
    Context parentContext = testing.runWithSpan("parent", Context::current);

    chatModel.stream(prompt())
        .contextWrite(
            reactorContext ->
                ContextPropagationOperator.storeOpenTelemetryContext(reactorContext, parentContext))
        .blockLast();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
                span -> span.hasName("chat " + MODEL).hasKind(CLIENT).hasParent(trace.getSpan(0))));
  }

  @Test
  void mapsKnownProviderFromInstrumentedModelClass() {
    TestChatModel openAiChatModel = new OpenAiChatModel(defaultOptions());

    testing.runWithSpan("parent", () -> openAiChatModel.call(prompt()));

    SpanContext spanContext = testing.waitForTraces(1).get(0).get(1).getSpanContext();
    assertCurrentSpanContext(openAiChatModel.getLastSpanContext(), spanContext);
    assertTraces("openai");
  }

  @Test
  void streamAggregatesChunksForEachChoice() {
    ChatResponse firstChunk =
        response(
            asList(generation("A ", null), generation("B ", null)),
            ChatResponseMetadata.builder().id("response-id").model(MODEL).build());
    ChatResponse secondChunk =
        response(
            asList(generation("one", "stop"), generation("two", "length")),
            ChatResponseMetadata.builder().build());
    ChatResponse usageChunk =
        response(emptyList(), ChatResponseMetadata.builder().usage(new DefaultUsage(3, 2)).build());
    ChatResponse metadataOnlyChunk = response(emptyList(), ChatResponseMetadata.builder().build());
    chatModel.setStreamPublisher(Flux.just(firstChunk, secondChunk, usageChunk, metadataOnlyChunk));

    testing.runWithSpan("parent", () -> chatModel.stream(prompt()).blockLast());

    SpanContext spanContext = testing.waitForTraces(1).get(0).get(1).getSpanContext();
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
                            equalTo(GEN_AI_REQUEST_FREQUENCY_PENALTY, 0.1),
                            equalTo(GEN_AI_REQUEST_MAX_TOKENS, 42L),
                            equalTo(GEN_AI_REQUEST_PRESENCE_PENALTY, 0.2),
                            equalTo(GEN_AI_REQUEST_STOP_SEQUENCES, singletonList("stop-sequence")),
                            equalTo(GEN_AI_REQUEST_TEMPERATURE, 0.3),
                            equalTo(GEN_AI_REQUEST_TOP_K, 4.0),
                            equalTo(GEN_AI_REQUEST_TOP_P, 0.5),
                            equalTo(GEN_AI_RESPONSE_FINISH_REASONS, asList("stop", "length")),
                            equalTo(GEN_AI_RESPONSE_ID, "response-id"),
                            equalTo(GEN_AI_RESPONSE_MODEL, MODEL),
                            equalTo(GEN_AI_USAGE_INPUT_TOKENS, 3L),
                            equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 2L),
                            equalTo(
                                stringKey("gen_ai.input.messages"),
                                messageSpanAttribute(
                                    "[{\"role\":\"user\",\"parts\":[{\"type\":\"text\",\"content\":\""
                                        + PROMPT
                                        + "\"}]}]")),
                            equalTo(
                                stringKey("gen_ai.output.messages"),
                                messageSpanAttribute(
                                    "[{\"role\":\"assistant\",\"parts\":[{\"type\":\"text\",\"content\":\"A one\"}],\"finish_reason\":\"stop\"},"
                                        + "{\"role\":\"assistant\",\"parts\":[{\"type\":\"text\",\"content\":\"B two\"}],\"finish_reason\":\"length\"}]")))));
    assertMetrics();
    assertMultiChoiceEvents(spanContext);
  }

  @Test
  void callOmitsEmptyResponseMetadata() {
    chatModel.setCallResponse(
        response(
            singletonList(generation(RESPONSE, "stop")),
            ChatResponseMetadata.builder().model(MODEL).build()));

    testing.runWithSpan("parent", () -> chatModel.call(prompt()));

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
                            equalTo(GEN_AI_REQUEST_FREQUENCY_PENALTY, 0.1),
                            equalTo(GEN_AI_REQUEST_MAX_TOKENS, 42L),
                            equalTo(GEN_AI_REQUEST_PRESENCE_PENALTY, 0.2),
                            equalTo(GEN_AI_REQUEST_STOP_SEQUENCES, singletonList("stop-sequence")),
                            equalTo(GEN_AI_REQUEST_TEMPERATURE, 0.3),
                            equalTo(GEN_AI_REQUEST_TOP_K, 4.0),
                            equalTo(GEN_AI_REQUEST_TOP_P, 0.5),
                            equalTo(GEN_AI_RESPONSE_FINISH_REASONS, singletonList("stop")),
                            equalTo(GEN_AI_RESPONSE_MODEL, MODEL),
                            equalTo(
                                stringKey("gen_ai.input.messages"),
                                messageSpanAttribute(
                                    "[{\"role\":\"user\",\"parts\":[{\"type\":\"text\",\"content\":\""
                                        + PROMPT
                                        + "\"}]}]")),
                            equalTo(
                                stringKey("gen_ai.output.messages"),
                                messageSpanAttribute(
                                    "[{\"role\":\"assistant\",\"parts\":[{\"type\":\"text\",\"content\":\""
                                        + RESPONSE
                                        + "\"}],\"finish_reason\":\"stop\"}]")))));
    assertMetricsWithoutTokenUsage();
  }

  @Test
  void nullMessageContentDoesNotAbortEvents() {
    chatModel.setCallResponse(
        response(
            asList(generation(null, "tool_calls"), generation(RESPONSE, "stop")),
            ChatResponseMetadata.builder()
                .id("response-id")
                .model(MODEL)
                .usage(new DefaultUsage(3, 2))
                .build()));
    Prompt prompt = new Prompt(asList(new AssistantMessage(null), new UserMessage(PROMPT)));

    testing.runWithSpan("parent", () -> chatModel.call(prompt));

    SpanContext spanContext = testing.waitForTraces(1).get(0).get(1).getSpanContext();
    testing.waitAndAssertLogRecords(
        log ->
            log.hasAttributesSatisfyingExactly(
                    equalTo(GEN_AI_PROVIDER_NAME, "test"),
                    equalTo(stringKey("event.name"), "gen_ai.assistant.message"))
                .hasSpanContext(spanContext)
                .hasBody(messageBody(null)),
        log ->
            log.hasAttributesSatisfyingExactly(
                    equalTo(GEN_AI_PROVIDER_NAME, "test"),
                    equalTo(stringKey("event.name"), "gen_ai.user.message"))
                .hasSpanContext(spanContext)
                .hasBody(messageBody(PROMPT)),
        log ->
            log.hasAttributesSatisfyingExactly(
                    equalTo(GEN_AI_PROVIDER_NAME, "test"),
                    equalTo(stringKey("event.name"), "gen_ai.choice"))
                .hasSpanContext(spanContext)
                .hasBody(choiceBody("tool_calls", 0, null)),
        log ->
            log.hasAttributesSatisfyingExactly(
                    equalTo(GEN_AI_PROVIDER_NAME, "test"),
                    equalTo(stringKey("event.name"), "gen_ai.choice"))
                .hasSpanContext(spanContext)
                .hasBody(choiceBody("stop", 1, RESPONSE)));
  }

  @Test
  void callErrorEndsSpan() {
    IllegalStateException error = new IllegalStateException("call failed");
    chatModel.setCallFailure(error);

    assertThatThrownBy(() -> testing.runWithSpan("parent", () -> chatModel.call(prompt())))
        .isSameAs(error);

    assertErrorTrace(error);
  }

  @Test
  void streamErrorEndsSpan() {
    IllegalStateException error = new IllegalStateException("stream failed");
    chatModel.setStreamPublisher(Flux.error(error));

    assertThatThrownBy(
            () -> testing.runWithSpan("parent", () -> chatModel.stream(prompt()).blockLast()))
        .isSameAs(error);

    assertErrorTrace(error);
  }

  @Test
  void synchronousStreamErrorEndsSpan() {
    IllegalStateException error = new IllegalStateException("stream failed synchronously");
    chatModel.setStreamFailure(error);

    assertThatThrownBy(() -> testing.runWithSpan("parent", () -> chatModel.stream(prompt())))
        .isSameAs(error);

    assertErrorTrace(error);
  }

  @Test
  void streamCancellationEndsSpan() {
    chatModel.setStreamPublisher(Flux.never());

    testing.runWithSpan(
        "parent",
        () -> {
          Disposable subscription = chatModel.stream(prompt()).subscribe();
          subscription.dispose();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
                span ->
                    span.hasName("chat " + MODEL)
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.unset())));
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
                "[{\"role\":\"user\",\"parts\":[{\"type\":\"text\",\"content\":\""
                    + repeatedContent(8192)
                    + "\"}]}]"));
  }

  @Test
  void streamedMessageSpanAttributeIsBoundedAtASurrogateBoundary() {
    ChatResponse firstChunk =
        response(
            singletonList(
                generation(repeatedContent(8191) + Character.toString((char) 0xD83D), null)),
            ChatResponseMetadata.builder().id("response-id").model(MODEL).build());
    ChatResponse secondChunk =
        response(
            singletonList(generation(Character.toString((char) 0xDE00) + "ignored", "stop")),
            ChatResponseMetadata.builder().usage(new DefaultUsage(3, 2)).build());
    chatModel.setStreamPublisher(Flux.just(firstChunk, secondChunk));

    testing.runWithSpan("parent", () -> chatModel.stream(prompt()).blockLast());

    assertThat(
            testing
                .waitForTraces(1)
                .get(0)
                .get(1)
                .getAttributes()
                .get(stringKey("gen_ai.output.messages")))
        .isEqualTo(
            messageSpanAttribute(
                "[{\"role\":\"assistant\",\"parts\":[{\"type\":\"text\",\"content\":\""
                    + repeatedContent(8191)
                    + "\"}],\"finish_reason\":\"stop\"}]"));
  }

  @Test
  void messageSpanAttributeEscapesJsonContent() {
    testing.runWithSpan("parent", () -> chatModel.call(new Prompt("line\n\"quoted\"")));

    assertThat(
            testing
                .waitForTraces(1)
                .get(0)
                .get(1)
                .getAttributes()
                .get(stringKey("gen_ai.input.messages")))
        .isEqualTo(
            messageSpanAttribute(
                "[{\"role\":\"user\",\"parts\":[{\"type\":\"text\",\"content\":\"line\\n\\\"quoted\\\"\"}]}]"));
  }

  private static void assertTraces(String provider) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
                span ->
                    span.hasName("chat " + MODEL)
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(GEN_AI_PROVIDER_NAME, provider),
                            equalTo(GEN_AI_OPERATION_NAME, CHAT),
                            equalTo(GEN_AI_REQUEST_MODEL, MODEL),
                            equalTo(GEN_AI_REQUEST_FREQUENCY_PENALTY, 0.1),
                            equalTo(GEN_AI_REQUEST_MAX_TOKENS, 42L),
                            equalTo(GEN_AI_REQUEST_PRESENCE_PENALTY, 0.2),
                            equalTo(GEN_AI_REQUEST_STOP_SEQUENCES, singletonList("stop-sequence")),
                            equalTo(GEN_AI_REQUEST_TEMPERATURE, 0.3),
                            equalTo(GEN_AI_REQUEST_TOP_K, 4.0),
                            equalTo(GEN_AI_REQUEST_TOP_P, 0.5),
                            equalTo(GEN_AI_RESPONSE_FINISH_REASONS, singletonList("stop")),
                            equalTo(GEN_AI_RESPONSE_ID, "response-id"),
                            equalTo(GEN_AI_RESPONSE_MODEL, MODEL),
                            equalTo(GEN_AI_USAGE_INPUT_TOKENS, 3L),
                            equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 2L),
                            equalTo(
                                stringKey("gen_ai.input.messages"),
                                messageSpanAttribute(
                                    "[{\"role\":\"user\",\"parts\":[{\"type\":\"text\",\"content\":\""
                                        + PROMPT
                                        + "\"}]}]")),
                            equalTo(
                                stringKey("gen_ai.output.messages"),
                                messageSpanAttribute(
                                    "[{\"role\":\"assistant\",\"parts\":[{\"type\":\"text\",\"content\":\""
                                        + RESPONSE
                                        + "\"}],\"finish_reason\":\"stop\"}]")))));
  }

  private static void assertErrorTrace(IllegalStateException error) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasKind(INTERNAL)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(error),
                span ->
                    span.hasName("chat " + MODEL)
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        .hasException(error)
                        .hasAttributesSatisfyingExactly(
                            equalTo(GEN_AI_PROVIDER_NAME, "test"),
                            equalTo(GEN_AI_OPERATION_NAME, CHAT),
                            equalTo(GEN_AI_REQUEST_MODEL, MODEL),
                            equalTo(GEN_AI_REQUEST_FREQUENCY_PENALTY, 0.1),
                            equalTo(GEN_AI_REQUEST_MAX_TOKENS, 42L),
                            equalTo(GEN_AI_REQUEST_PRESENCE_PENALTY, 0.2),
                            equalTo(GEN_AI_REQUEST_STOP_SEQUENCES, singletonList("stop-sequence")),
                            equalTo(GEN_AI_REQUEST_TEMPERATURE, 0.3),
                            equalTo(GEN_AI_REQUEST_TOP_K, 4.0),
                            equalTo(GEN_AI_REQUEST_TOP_P, 0.5),
                            equalTo(ERROR_TYPE, IllegalStateException.class.getName()),
                            equalTo(
                                stringKey("gen_ai.input.messages"),
                                messageSpanAttribute(
                                    "[{\"role\":\"user\",\"parts\":[{\"type\":\"text\",\"content\":\""
                                        + PROMPT
                                        + "\"}]}]")))));
  }

  private static void assertMetrics() {
    testing.waitAndAssertMetrics(
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
                                        equalTo(GEN_AI_PROVIDER_NAME, "test"),
                                        equalTo(GEN_AI_OPERATION_NAME, CHAT),
                                        equalTo(GEN_AI_REQUEST_MODEL, MODEL),
                                        equalTo(GEN_AI_RESPONSE_MODEL, MODEL)))),
        metric ->
            metric
                .hasName("gen_ai.client.token.usage")
                .hasHistogramSatisfying(
                    histogram ->
                        histogram.hasPointsSatisfying(
                            point ->
                                point
                                    .hasSum(3.0)
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(GEN_AI_PROVIDER_NAME, "test"),
                                        equalTo(GEN_AI_OPERATION_NAME, CHAT),
                                        equalTo(GEN_AI_REQUEST_MODEL, MODEL),
                                        equalTo(GEN_AI_RESPONSE_MODEL, MODEL),
                                        equalTo(GEN_AI_TOKEN_TYPE, INPUT)),
                            point ->
                                point
                                    .hasSum(2.0)
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(GEN_AI_PROVIDER_NAME, "test"),
                                        equalTo(GEN_AI_OPERATION_NAME, CHAT),
                                        equalTo(GEN_AI_REQUEST_MODEL, MODEL),
                                        equalTo(GEN_AI_RESPONSE_MODEL, MODEL),
                                        equalTo(GEN_AI_TOKEN_TYPE, OUTPUT)))));
  }

  private static void assertMetricsWithoutTokenUsage() {
    testing.waitAndAssertMetrics(
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
                                        equalTo(GEN_AI_PROVIDER_NAME, "test"),
                                        equalTo(GEN_AI_OPERATION_NAME, CHAT),
                                        equalTo(GEN_AI_REQUEST_MODEL, MODEL),
                                        equalTo(GEN_AI_RESPONSE_MODEL, MODEL)))));
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "gen_ai.client.token.usage", metrics -> metrics.isEmpty());
  }

  private static void assertMessageEvents(SpanContext spanContext) {
    testing.waitAndAssertLogRecords(
        log ->
            log.hasAttributesSatisfyingExactly(
                    equalTo(GEN_AI_PROVIDER_NAME, "test"),
                    equalTo(stringKey("event.name"), "gen_ai.user.message"))
                .hasSpanContext(spanContext)
                .hasBody(messageBody(PROMPT)),
        log ->
            log.hasAttributesSatisfyingExactly(
                    equalTo(GEN_AI_PROVIDER_NAME, "test"),
                    equalTo(stringKey("event.name"), "gen_ai.choice"))
                .hasSpanContext(spanContext)
                .hasBody(choiceBody("stop", 0, RESPONSE)));
  }

  private static void assertMultiChoiceEvents(SpanContext spanContext) {
    testing.waitAndAssertLogRecords(
        log ->
            log.hasAttributesSatisfyingExactly(
                    equalTo(GEN_AI_PROVIDER_NAME, "test"),
                    equalTo(stringKey("event.name"), "gen_ai.user.message"))
                .hasSpanContext(spanContext)
                .hasBody(messageBody(PROMPT)),
        log ->
            log.hasAttributesSatisfyingExactly(
                    equalTo(GEN_AI_PROVIDER_NAME, "test"),
                    equalTo(stringKey("event.name"), "gen_ai.choice"))
                .hasSpanContext(spanContext)
                .hasBody(choiceBody("stop", 0, "A one")),
        log ->
            log.hasAttributesSatisfyingExactly(
                    equalTo(GEN_AI_PROVIDER_NAME, "test"),
                    equalTo(stringKey("event.name"), "gen_ai.choice"))
                .hasSpanContext(spanContext)
                .hasBody(choiceBody("length", 1, "B two")));
  }

  private static Value<?> messageBody(String content) {
    return CAPTURE_MESSAGE_CONTENT && content != null
        ? Value.of(KeyValue.of("content", Value.of(content)))
        : Value.of(emptyMap());
  }

  private static Value<?> choiceBody(String finishReason, int index, String content) {
    Value<?> message =
        CAPTURE_MESSAGE_CONTENT && content != null
            ? Value.of(KeyValue.of("content", Value.of(content)))
            : Value.of(emptyMap());
    return Value.of(
        KeyValue.of("finish_reason", Value.of(finishReason)),
        KeyValue.of("index", Value.of(index)),
        KeyValue.of("message", message));
  }

  private static Prompt prompt() {
    return new Prompt(PROMPT);
  }

  private static DefaultChatOptions defaultOptions() {
    DefaultChatOptions options = new DefaultChatOptions();
    options.setModel(MODEL);
    options.setFrequencyPenalty(0.1);
    options.setMaxTokens(42);
    options.setPresencePenalty(0.2);
    options.setStopSequences(singletonList("stop-sequence"));
    options.setTemperature(0.3);
    options.setTopK(4);
    options.setTopP(0.5);
    return options;
  }

  private static ChatResponse response(
      List<Generation> generations, ChatResponseMetadata metadata) {
    return new ChatResponse(generations, metadata);
  }

  private static Generation generation(String content, String finishReason) {
    ChatGenerationMetadata metadata =
        finishReason == null
            ? ChatGenerationMetadata.builder().build()
            : ChatGenerationMetadata.builder().finishReason(finishReason).build();
    return new Generation(new AssistantMessage(content), metadata);
  }

  private static String repeatedContent(int length) {
    StringBuilder content = new StringBuilder(length);
    for (int index = 0; index < length; index++) {
      content.append('a');
    }
    return content.toString();
  }

  private static void assertCurrentSpanContext(SpanContext current, SpanContext expected) {
    assertThat(current.getTraceId()).isEqualTo(expected.getTraceId());
    assertThat(current.getSpanId()).isEqualTo(expected.getSpanId());
  }

  private static <T> T messageSpanAttribute(T value) {
    return EXPERIMENTAL_ATTRIBUTES ? value : null;
  }
}
