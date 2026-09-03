/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0;

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
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_STREAM;
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
import io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0.app.TestChatModel;
import io.opentelemetry.javaagent.testing.common.TestAgentListenerAccess;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.DefaultChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

@SuppressWarnings("deprecation") // using deprecated semconv
class ChatModelTest {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-ai-1.0";
  private static final String MODEL = "test-model";
  private static final String PROMPT = "Tell me about traces";
  private static final String RESPONSE = "A trace represents an end-to-end request.";
  private static final String TOOL_CALL_ID = "call_weather";
  private static final String TOOL_NAME = "get_weather";
  private static final String TOOL_ARGUMENTS = "{\"location\":\"Paris\"}";
  private static final String TOOL_RESPONSE = "rainy, 57F";
  private static final String SECOND_TOOL_CALL_ID = "call_time";
  private static final String SECOND_TOOL_NAME = "get_time";
  private static final String SECOND_TOOL_RESPONSE = "10:00 UTC";
  private static final String MEDIA_URL = "https://example.com/weather.png";
  private static final boolean CAPTURE_MESSAGE_CONTENT =
      Boolean.getBoolean("otel.instrumentation.genai.capture-message-content");
  private static final boolean EXPERIMENTAL_ATTRIBUTES =
      Boolean.getBoolean(
          "otel.instrumentation.spring-ai.experimental.capture-message-content-as-span-attributes.enabled");
  private static final int MESSAGE_CONTENT_SPAN_ATTRIBUTE_MAX_LENGTH =
      Integer.getInteger(
          "otel.instrumentation.spring-ai.experimental.message-content-span-attribute.max-length",
          8192);

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private final TestChatModel chatModel = new TestChatModel(defaultOptions());

  @Test
  void callUsesDefaultOptionsAndCapturesRequestFields() {
    testing.runWithSpan("parent", () -> chatModel.call(prompt()));

    SpanContext spanContext = testing.waitForTraces(1).get(0).get(1).getSpanContext();
    assertCurrentSpanContext(chatModel.getLastSpanContext(), spanContext);
    assertTraces("test", false);
    assertMetrics();
    assertMessageEvents(spanContext);
  }

  @Test
  void nestedCallEndsOneSpan() {
    chatModel.setCallDelegate(new TestChatModel(defaultOptions()));

    testing.runWithSpan("parent", () -> chatModel.call(prompt()));

    assertTraces("test", false);
  }

  @Test
  void callImplementedWithStreamEndsOneSpan() {
    chatModel.setCallStreamDelegate(new TestChatModel(defaultOptions()));

    testing.runWithSpan("parent", () -> chatModel.call(prompt()));

    assertTraces("test", false);
  }

  @Test
  void instrumentationSetupFailureDoesNotLeakCallDepth() {
    chatModel.setDefaultOptionsFailure(new IllegalStateException("default options failed"));
    testing.runWithSpan("failed setup", () -> chatModel.call(prompt()));
    assertThat(TestAgentListenerAccess.getAndResetAdviceFailureCount()).isEqualTo(1);

    chatModel.setDefaultOptionsFailure(null);
    testing.runWithSpan("call parent", () -> chatModel.call(prompt()));
    testing.runWithSpan("stream parent", () -> chatModel.stream(prompt()).blockLast());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("failed setup").hasKind(INTERNAL).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("call parent").hasKind(INTERNAL).hasNoParent(),
                span -> span.hasName("chat " + MODEL).hasKind(CLIENT).hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("stream parent").hasKind(INTERNAL).hasNoParent(),
                span -> span.hasName("chat " + MODEL).hasKind(CLIENT).hasParent(trace.getSpan(0))));
  }

  @Test
  void optionalResponseProcessingFailureStillEndsSpan() {
    ChatResponse response =
        response(
            singletonList(generation(RESPONSE, "stop")),
            ChatResponseMetadata.builder()
                .id("response-id")
                .model(MODEL)
                .usage(new DefaultUsage(3, 2))
                .build());
    chatModel.setCallResponse(
        new ChatResponse(response.getResults(), response.getMetadata()) {
          private boolean firstGetResults = true;

          @Override
          public List<Generation> getResults() {
            if (firstGetResults) {
              firstGetResults = false;
              throw new IllegalStateException("response processing failed");
            }
            return super.getResults();
          }
        });

    testing.runWithSpan("parent", () -> chatModel.call(prompt()));

    assertThat(TestAgentListenerAccess.getAndResetAdviceFailureCount()).isZero();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
                span -> span.hasName("chat " + MODEL).hasKind(CLIENT).hasParent(trace.getSpan(0))));
    assertMetrics();
  }

  @Test
  void callResponseEventSurvivesSpanAttributeSerializationFailure() {
    AssistantMessage output =
        new AssistantMessage(RESPONSE) {
          @Override
          public List<Media> getMedia() {
            throw new IllegalStateException("media processing failed");
          }
        };
    chatModel.setCallResponse(
        response(
            singletonList(generation(output, "stop")),
            ChatResponseMetadata.builder()
                .id("response-id")
                .model(MODEL)
                .usage(new DefaultUsage(3, 2))
                .build()));

    testing.runWithSpan("parent", () -> chatModel.call(prompt()));

    SpanContext spanContext = testing.waitForTraces(1).get(0).get(1).getSpanContext();
    assertTraces("test", false, false);
    assertMetrics();
    assertMessageEvents(spanContext);
  }

  @Test
  void streamResponseEventSurvivesSpanAttributeSerializationFailure() {
    AssistantMessage output =
        new AssistantMessage(RESPONSE) {
          @Override
          public List<Media> getMedia() {
            throw new IllegalStateException("media processing failed");
          }
        };
    chatModel.setStreamPublisher(
        Flux.just(
            response(
                singletonList(generation(output, "stop")),
                ChatResponseMetadata.builder()
                    .id("response-id")
                    .model(MODEL)
                    .usage(new DefaultUsage(3, 2))
                    .build())));

    testing.runWithSpan("parent", () -> chatModel.stream(prompt()).blockLast());

    SpanContext spanContext = testing.waitForTraces(1).get(0).get(1).getSpanContext();
    assertTraces("test", true, false);
    assertMetrics();
    assertMessageEvents(spanContext);
  }

  @Test
  void optionalPromptProcessingFailureStillTracesCall() {
    testing.runWithSpan("parent", () -> chatModel.call(promptWithFailingInstructions()));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
                span -> span.hasName("chat " + MODEL).hasKind(CLIENT).hasParent(trace.getSpan(0))));
    assertMetrics();
  }

  @Test
  void optionalPromptProcessingFailureStillTracesStream() {
    testing.runWithSpan(
        "parent", () -> chatModel.stream(promptWithFailingInstructions()).blockLast());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
                span -> span.hasName("chat " + MODEL).hasKind(CLIENT).hasParent(trace.getSpan(0))));
    assertMetrics();
  }

  @SuppressWarnings("PublicApiNamedStreamShouldReturnStream")
  @Test
  void stream() {
    testing.runWithSpan("parent", () -> chatModel.stream(prompt()).blockLast());

    SpanContext spanContext = testing.waitForTraces(1).get(0).get(1).getSpanContext();
    assertCurrentSpanContext(chatModel.getLastSpanContext(), spanContext);
    assertTraces("test", true);
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
  void deferredStreamDelegateEndsOneSpan() {
    TestChatModel delegate = new TestChatModel(defaultOptions());
    chatModel.setDeferredStreamDelegate(delegate);

    testing.runWithSpan("parent", () -> chatModel.stream(prompt()).blockLast());

    SpanContext spanContext = testing.waitForTraces(1).get(0).get(1).getSpanContext();
    assertCurrentSpanContext(delegate.getLastSpanContext(), spanContext);
    assertTraces("test", true);
    assertMetrics();
    assertMessageEvents(spanContext);
  }

  @Test
  void streamNestedInDownstreamCallbackIsInstrumented() {
    chatModel.stream(prompt())
        .flatMap(response -> chatModel.stream(new Prompt("Tell me more about traces")))
        .blockLast();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("chat " + MODEL).hasKind(CLIENT).hasNoParent(),
                span -> span.hasName("chat " + MODEL).hasKind(CLIENT).hasParent(trace.getSpan(0))));
  }

  @Test
  void streamAggregatesChunksByProviderChoiceIndex() {
    ChatResponse firstChunk =
        response(
            singletonList(generationWithChoiceIndex("A ", null, 1)),
            ChatResponseMetadata.builder().id("response-id").model(MODEL).build());
    ChatResponse secondChunk =
        response(
            singletonList(generationWithChoiceIndex("B ", null, 3)),
            ChatResponseMetadata.builder().build());
    ChatResponse thirdChunk =
        response(
            singletonList(generationWithChoiceIndex("one", "stop", 1)),
            ChatResponseMetadata.builder().build());
    ChatResponse fourthChunk =
        response(
            singletonList(generationWithChoiceIndex("two", "length", 3)),
            ChatResponseMetadata.builder().build());
    ChatResponse usageChunk =
        response(emptyList(), ChatResponseMetadata.builder().usage(new DefaultUsage(3, 2)).build());
    ChatResponse metadataOnlyChunk = response(emptyList(), ChatResponseMetadata.builder().build());
    chatModel.setStreamPublisher(
        Flux.just(firstChunk, secondChunk, thirdChunk, fourthChunk, usageChunk, metadataOnlyChunk));

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
                            equalTo(GEN_AI_REQUEST_STREAM, true),
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
  void streamDeduplicatesMediaByEmittedMetadata() {
    Media firstMedia =
        Media.builder()
            .mimeType(Media.Format.IMAGE_PNG)
            .data(new byte[] {1, 2, 3})
            .name("image")
            .build();
    Media secondMedia =
        Media.builder()
            .mimeType(Media.Format.IMAGE_PNG)
            .data(new byte[] {4, 5, 6})
            .name("image")
            .build();
    chatModel.setStreamPublisher(
        Flux.just(
            response(
                singletonList(
                    generation(assistantMessage("", emptyList(), singletonList(firstMedia)), null)),
                ChatResponseMetadata.builder().id("response-id").model(MODEL).build()),
            response(
                singletonList(
                    generation(
                        assistantMessage("", emptyList(), singletonList(secondMedia)), "stop")),
                ChatResponseMetadata.builder().usage(new DefaultUsage(3, 2)).build())));

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
                "[{\"role\":\"assistant\",\"parts\":[{\"type\":\"media\",\"mime_type\":\"image/png\",\"modality\":\"image\",\"name\":\"image\"}],\"finish_reason\":\"stop\"}]"));
  }

  @Test
  void streamAggregatesToolCallsAcrossChunks() {
    ChatResponse firstChunk =
        response(
            singletonList(
                generation(
                    assistantMessage(
                        "", singletonList(toolCall(TOOL_CALL_ID, "function", TOOL_NAME, "{\"loc"))),
                    null)),
            ChatResponseMetadata.builder().id("response-id").model(MODEL).build());
    ChatResponse secondChunk =
        response(
            singletonList(
                generation(
                    assistantMessage(
                        "", singletonList(toolCall(null, null, null, "ation\":\"Paris\"}"))),
                    "tool_calls")),
            ChatResponseMetadata.builder().usage(new DefaultUsage(3, 2)).build());
    chatModel.setStreamPublisher(Flux.just(firstChunk, secondChunk));

    testing.runWithSpan("parent", () -> chatModel.stream(prompt()).blockLast());

    SpanContext spanContext = testing.waitForTraces(1).get(0).get(1).getSpanContext();
    assertThat(
            testing
                .waitForTraces(1)
                .get(0)
                .get(1)
                .getAttributes()
                .get(stringKey("gen_ai.output.messages")))
        .isEqualTo(
            messageSpanAttribute(
                "[{\"role\":\"assistant\",\"parts\":[{\"type\":\"tool_call\",\"id\":\""
                    + TOOL_CALL_ID
                    + "\",\"name\":\""
                    + TOOL_NAME
                    + "\",\"arguments\":\"{\\\"location\\\":\\\"Paris\\\"}\"}],\"finish_reason\":\"tool_calls\"}]"));
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
                .hasBody(choiceBodyWithToolCall("")));
  }

  @Test
  void streamAggregatesParallelToolCallDeltasByIndex() {
    ChatResponse firstChunk =
        response(
            singletonList(
                generation(
                    assistantMessage(
                        "",
                        asList(
                            toolCall(TOOL_CALL_ID, "function", TOOL_NAME, "{\"loc"),
                            toolCall("call_time", "function", "get_time", "{\"time"))),
                    null)),
            ChatResponseMetadata.builder().id("response-id").model(MODEL).build());
    ChatResponse secondChunk =
        response(
            singletonList(
                generation(
                    assistantMessage(
                        "",
                        asList(
                            toolCall(null, null, null, "ation\":\"Paris\"}"),
                            toolCall(null, null, null, "zone\":\"UTC\"}"))),
                    "tool_calls")),
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
                "[{\"role\":\"assistant\",\"parts\":[{\"type\":\"tool_call\",\"id\":\""
                    + TOOL_CALL_ID
                    + "\",\"name\":\""
                    + TOOL_NAME
                    + "\",\"arguments\":\"{\\\"location\\\":\\\"Paris\\\"}\"},"
                    + "{\"type\":\"tool_call\",\"id\":\"call_time\",\"name\":\"get_time\",\"arguments\":\"{\\\"timezone\\\":\\\"UTC\\\"}\"}],\"finish_reason\":\"tool_calls\"}]"));
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
            asList(generation((String) null, "tool_calls"), generation(RESPONSE, "stop")),
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
  void structuredMessageSpanAttributePreservesToolCallsToolResponsesAndMedia() {
    chatModel.setCallResponse(
        response(
            singletonList(generation(outputMessageWithToolCall(), "tool_calls")),
            ChatResponseMetadata.builder().id("response-id").model(MODEL).build()));

    testing.runWithSpan("parent", () -> chatModel.call(toolCallingPrompt()));

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
                    + PROMPT
                    + "\"},{\"type\":\"uri\",\"mime_type\":\"image/png\",\"modality\":\"image\",\"uri\":\""
                    + MEDIA_URL
                    + "\"}]},"
                    + "{\"role\":\"assistant\",\"parts\":[{\"type\":\"tool_call\",\"id\":\""
                    + TOOL_CALL_ID
                    + "\",\"name\":\""
                    + TOOL_NAME
                    + "\",\"arguments\":\"{\\\"location\\\":\\\"Paris\\\"}\"}]},"
                    + "{\"role\":\"tool\",\"parts\":[{\"type\":\"tool_call_response\",\"id\":\""
                    + TOOL_CALL_ID
                    + "\",\"name\":\""
                    + TOOL_NAME
                    + "\",\"response\":\""
                    + TOOL_RESPONSE
                    + "\"}]}]"));
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
                    + RESPONSE
                    + "\"},{\"type\":\"tool_call\",\"id\":\""
                    + TOOL_CALL_ID
                    + "\",\"name\":\""
                    + TOOL_NAME
                    + "\",\"arguments\":\"{\\\"location\\\":\\\"Paris\\\"}\"}],\"finish_reason\":\"tool_calls\"}]"));
  }

  @Test
  void messageEventsPreserveToolCallsAndToolResponses() {
    chatModel.setCallResponse(
        response(
            singletonList(generation(outputMessageWithToolCall(), "tool_calls")),
            ChatResponseMetadata.builder().id("response-id").model(MODEL).build()));

    testing.runWithSpan("parent", () -> chatModel.call(toolCallingPrompt()));

    SpanContext spanContext = testing.waitForTraces(1).get(0).get(1).getSpanContext();
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
                    equalTo(stringKey("event.name"), "gen_ai.assistant.message"))
                .hasSpanContext(spanContext)
                .hasBody(assistantToolCallBody()),
        log ->
            log.hasAttributesSatisfyingExactly(
                    equalTo(GEN_AI_PROVIDER_NAME, "test"),
                    equalTo(stringKey("event.name"), "gen_ai.tool.message"))
                .hasSpanContext(spanContext)
                .hasBody(toolResponseBody()),
        log ->
            log.hasAttributesSatisfyingExactly(
                    equalTo(GEN_AI_PROVIDER_NAME, "test"),
                    equalTo(stringKey("event.name"), "gen_ai.choice"))
                .hasSpanContext(spanContext)
                .hasBody(choiceBodyWithToolCall()));
  }

  @Test
  void messageEventsEmitOneEventPerToolResponse() {
    chatModel.setCallResponse(
        response(
            singletonList(generation(RESPONSE, "stop")),
            ChatResponseMetadata.builder().id("response-id").model(MODEL).build()));
    Prompt prompt =
        new Prompt(
            singletonList(
                toolResponseMessage(
                    asList(
                        toolResponse(),
                        new ToolResponseMessage.ToolResponse(
                            SECOND_TOOL_CALL_ID, SECOND_TOOL_NAME, SECOND_TOOL_RESPONSE)))));

    testing.runWithSpan("parent", () -> chatModel.call(prompt));

    SpanContext spanContext = testing.waitForTraces(1).get(0).get(1).getSpanContext();
    testing.waitAndAssertLogRecords(
        log ->
            log.hasAttributesSatisfyingExactly(
                    equalTo(GEN_AI_PROVIDER_NAME, "test"),
                    equalTo(stringKey("event.name"), "gen_ai.tool.message"))
                .hasSpanContext(spanContext)
                .hasBody(toolResponseBody()),
        log ->
            log.hasAttributesSatisfyingExactly(
                    equalTo(GEN_AI_PROVIDER_NAME, "test"),
                    equalTo(stringKey("event.name"), "gen_ai.tool.message"))
                .hasSpanContext(spanContext)
                .hasBody(
                    toolResponseBody(SECOND_TOOL_CALL_ID, SECOND_TOOL_NAME, SECOND_TOOL_RESPONSE)),
        log ->
            log.hasAttributesSatisfyingExactly(
                    equalTo(GEN_AI_PROVIDER_NAME, "test"),
                    equalTo(stringKey("event.name"), "gen_ai.choice"))
                .hasSpanContext(spanContext)
                .hasBody(choiceBody("stop", 0, RESPONSE)));
  }

  @Test
  void callErrorEndsSpan() {
    IllegalStateException error = new IllegalStateException("call failed");
    chatModel.setCallFailure(error);

    assertThatThrownBy(() -> testing.runWithSpan("parent", () -> chatModel.call(prompt())))
        .isSameAs(error);

    assertErrorTrace(error, false);
  }

  @Test
  void streamErrorEndsSpan() {
    IllegalStateException error = new IllegalStateException("stream failed");
    chatModel.setStreamPublisher(Flux.error(error));

    assertThatThrownBy(
            () -> testing.runWithSpan("parent", () -> chatModel.stream(prompt()).blockLast()))
        .isSameAs(error);

    assertErrorTrace(error, true);
  }

  @Test
  void synchronousStreamErrorEndsSpan() {
    IllegalStateException error = new IllegalStateException("stream failed synchronously");
    chatModel.setStreamFailure(error);

    assertThatThrownBy(() -> testing.runWithSpan("parent", () -> chatModel.stream(prompt())))
        .isSameAs(error);

    assertErrorTrace(error, true);
  }

  @Test
  void nestedSynchronousStreamErrorEndsOneSpan() {
    IllegalStateException error = new IllegalStateException("nested stream failed synchronously");
    TestChatModel delegate = new TestChatModel(defaultOptions());
    delegate.setStreamFailure(error);
    chatModel.setStreamDelegate(delegate);

    assertThatThrownBy(() -> testing.runWithSpan("parent", () -> chatModel.stream(prompt())))
        .isSameAs(error);

    assertErrorTrace(error, true);
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
  void messageSpanAttributeUsesConfiguredMaxLength() {
    String content = repeatedContent(MESSAGE_CONTENT_SPAN_ATTRIBUTE_MAX_LENGTH + 1);
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
                    + repeatedContent(MESSAGE_CONTENT_SPAN_ATTRIBUTE_MAX_LENGTH)
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
  void streamedMessageSpanAttributeUsesConfiguredMaxLength() {
    String content = repeatedContent(MESSAGE_CONTENT_SPAN_ATTRIBUTE_MAX_LENGTH + 1);
    chatModel.setStreamPublisher(
        Flux.just(
            response(
                singletonList(generation(content, "stop")),
                ChatResponseMetadata.builder()
                    .id("response-id")
                    .model(MODEL)
                    .usage(new DefaultUsage(3, 2))
                    .build())));

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
                    + repeatedContent(MESSAGE_CONTENT_SPAN_ATTRIBUTE_MAX_LENGTH)
                    + "\"}],\"finish_reason\":\"stop\"}]"));
  }

  @Test
  void streamedToolCallArgumentsUseConfiguredMaxLength() {
    String arguments = repeatedContent(MESSAGE_CONTENT_SPAN_ATTRIBUTE_MAX_LENGTH + 1);
    ChatResponse firstChunk =
        response(
            singletonList(
                generation(
                    assistantMessage(
                        "",
                        singletonList(
                            toolCall(
                                TOOL_CALL_ID,
                                "function",
                                TOOL_NAME,
                                arguments.substring(
                                    0, MESSAGE_CONTENT_SPAN_ATTRIBUTE_MAX_LENGTH)))),
                    null)),
            ChatResponseMetadata.builder().id("response-id").model(MODEL).build());
    ChatResponse secondChunk =
        response(
            singletonList(
                generation(
                    assistantMessage(
                        "",
                        singletonList(
                            toolCall(
                                null,
                                null,
                                null,
                                arguments.substring(MESSAGE_CONTENT_SPAN_ATTRIBUTE_MAX_LENGTH)))),
                    "tool_calls")),
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
                "[{\"role\":\"assistant\",\"parts\":[{\"type\":\"tool_call\",\"id\":\""
                    + TOOL_CALL_ID
                    + "\",\"name\":\""
                    + TOOL_NAME
                    + "\",\"arguments\":\""
                    + repeatedContent(MESSAGE_CONTENT_SPAN_ATTRIBUTE_MAX_LENGTH)
                    + "\"}],\"finish_reason\":\"tool_calls\"}]"));
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

  @Test
  void messageSpanAttributeDropsRelativeUriMedia() {
    Prompt prompt =
        new Prompt(
            singletonList(
                UserMessage.builder()
                    .text(PROMPT)
                    .media(
                        Media.builder()
                            .mimeType(Media.Format.IMAGE_PNG)
                            .data(URI.create("/relative/path.png"))
                            .build())
                    .build()));

    testing.runWithSpan("parent", () -> chatModel.call(prompt));

    String inputMessages =
        testing
            .waitForTraces(1)
            .get(0)
            .get(1)
            .getAttributes()
            .get(stringKey("gen_ai.input.messages"));
    if (!EXPERIMENTAL_ATTRIBUTES) {
      assertThat(inputMessages).isNull();
      return;
    }

    assertThat(inputMessages)
        .contains("\"type\":\"media\"")
        .contains("\"mime_type\":\"image/png\"")
        .contains("\"modality\":\"image\"")
        .doesNotContain("\"type\":\"uri\"");
  }

  private static void assertTraces(String provider, boolean streaming) {
    assertTraces(provider, streaming, true);
  }

  private static void assertTraces(
      String provider, boolean streaming, boolean expectOutputMessages) {
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
                            equalTo(GEN_AI_REQUEST_STREAM, streaming ? true : null),
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
                                expectOutputMessages
                                    ? messageSpanAttribute(
                                        "[{\"role\":\"assistant\",\"parts\":[{\"type\":\"text\",\"content\":\""
                                            + RESPONSE
                                            + "\"}],\"finish_reason\":\"stop\"}]")
                                    : null))));
  }

  private static void assertErrorTrace(IllegalStateException error, boolean streaming) {
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
                            equalTo(GEN_AI_REQUEST_STREAM, streaming ? true : null),
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
                .hasBody(choiceBody("stop", 1, "A one")),
        log ->
            log.hasAttributesSatisfyingExactly(
                    equalTo(GEN_AI_PROVIDER_NAME, "test"),
                    equalTo(stringKey("event.name"), "gen_ai.choice"))
                .hasSpanContext(spanContext)
                .hasBody(choiceBody("length", 3, "B two")));
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

  private static Value<?> assistantToolCallBody() {
    return Value.of(KeyValue.of("tool_calls", toolCallsValue()));
  }

  private static Value<?> toolResponseBody() {
    return toolResponseBody(TOOL_CALL_ID, TOOL_NAME, TOOL_RESPONSE);
  }

  private static Value<?> toolResponseBody(String id, String name, String response) {
    if (CAPTURE_MESSAGE_CONTENT) {
      return Value.of(
          KeyValue.of("id", Value.of(id)),
          KeyValue.of("name", Value.of(name)),
          KeyValue.of("content", Value.of(response)));
    }
    return Value.of(KeyValue.of("id", Value.of(id)), KeyValue.of("name", Value.of(name)));
  }

  private static Value<?> choiceBodyWithToolCall() {
    return choiceBodyWithToolCall(RESPONSE);
  }

  private static Value<?> choiceBodyWithToolCall(String content) {
    Value<?> message =
        CAPTURE_MESSAGE_CONTENT
            ? Value.of(
                KeyValue.of("content", Value.of(content)),
                KeyValue.of("tool_calls", toolCallsValue()))
            : Value.of(KeyValue.of("tool_calls", toolCallsValue()));
    return Value.of(
        KeyValue.of("finish_reason", Value.of("tool_calls")),
        KeyValue.of("index", Value.of(0)),
        KeyValue.of("message", message));
  }

  private static Value<?> toolCallsValue() {
    return Value.of(singletonList(toolCallValue()));
  }

  private static Value<?> toolCallValue() {
    Value<?> function =
        CAPTURE_MESSAGE_CONTENT
            ? Value.of(
                KeyValue.of("name", Value.of(TOOL_NAME)),
                KeyValue.of("arguments", Value.of(TOOL_ARGUMENTS)))
            : Value.of(KeyValue.of("name", Value.of(TOOL_NAME)));
    return Value.of(
        KeyValue.of("function", function),
        KeyValue.of("id", Value.of(TOOL_CALL_ID)),
        KeyValue.of("type", Value.of("function")));
  }

  private static Prompt prompt() {
    return new Prompt(PROMPT);
  }

  private static Prompt promptWithFailingInstructions() {
    return new Prompt(PROMPT) {
      @Override
      public List<Message> getInstructions() {
        throw new IllegalStateException("prompt processing failed");
      }
    };
  }

  private static Prompt toolCallingPrompt() {
    return new Prompt(
        asList(
            UserMessage.builder()
                .text(PROMPT)
                .media(
                    Media.builder()
                        .mimeType(Media.Format.IMAGE_PNG)
                        .data(URI.create(MEDIA_URL))
                        .build())
                .build(),
            assistantMessage(null, singletonList(toolCall())),
            toolResponseMessage(singletonList(toolResponse()))));
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
    return generation(new AssistantMessage(content), finishReason);
  }

  private static Generation generation(AssistantMessage message, String finishReason) {
    ChatGenerationMetadata metadata =
        finishReason == null
            ? ChatGenerationMetadata.builder().build()
            : ChatGenerationMetadata.builder().finishReason(finishReason).build();
    return new Generation(message, metadata);
  }

  private static Generation generationWithChoiceIndex(
      String content, String finishReason, int choiceIndex) {
    return generation(
        assistantMessage(content, emptyList(), emptyList(), Map.of("index", choiceIndex)),
        finishReason);
  }

  private static AssistantMessage outputMessageWithToolCall() {
    return assistantMessage(RESPONSE, singletonList(toolCall()));
  }

  private static AssistantMessage assistantMessage(
      String content, List<AssistantMessage.ToolCall> toolCalls) {
    return assistantMessage(content, toolCalls, emptyList());
  }

  private static AssistantMessage assistantMessage(
      String content, List<AssistantMessage.ToolCall> toolCalls, List<Media> media) {
    return assistantMessage(content, toolCalls, media, emptyMetadata());
  }

  private static AssistantMessage assistantMessage(
      String content,
      List<AssistantMessage.ToolCall> toolCalls,
      List<Media> media,
      Map<String, Object> metadata) {
    try {
      Object builder = AssistantMessage.class.getMethod("builder").invoke(null);
      invokeBuilder(builder, "content", String.class, content);
      invokeBuilder(builder, "properties", Map.class, metadata);
      invokeBuilder(builder, "toolCalls", List.class, toolCalls);
      invokeBuilder(builder, "media", List.class, media);
      return (AssistantMessage) builder.getClass().getMethod("build").invoke(builder);
    } catch (NoSuchMethodException e) {
      return assistantMessageWithConstructor(content, toolCalls, media, metadata, e);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Could not create AssistantMessage", e);
    }
  }

  private static AssistantMessage assistantMessageWithConstructor(
      String content,
      List<AssistantMessage.ToolCall> toolCalls,
      List<Media> media,
      Map<String, Object> metadata,
      NoSuchMethodException e) {
    try {
      return AssistantMessage.class
          .getConstructor(String.class, Map.class, List.class, List.class)
          .newInstance(content, metadata, toolCalls, media);
    } catch (ReflectiveOperationException f) {
      e.addSuppressed(f);
      throw new IllegalStateException("Could not create AssistantMessage", e);
    }
  }

  private static ToolResponseMessage toolResponseMessage(
      List<ToolResponseMessage.ToolResponse> responses) {
    try {
      Object builder = ToolResponseMessage.class.getMethod("builder").invoke(null);
      invokeBuilder(builder, "responses", List.class, responses);
      invokeBuilder(builder, "metadata", Map.class, emptyMetadata());
      return (ToolResponseMessage) builder.getClass().getMethod("build").invoke(builder);
    } catch (NoSuchMethodException e) {
      return toolResponseMessageWithConstructor(responses, e);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Could not create ToolResponseMessage", e);
    }
  }

  private static ToolResponseMessage toolResponseMessageWithConstructor(
      List<ToolResponseMessage.ToolResponse> responses, NoSuchMethodException e) {
    try {
      return ToolResponseMessage.class
          .getConstructor(List.class, Map.class)
          .newInstance(responses, emptyMetadata());
    } catch (ReflectiveOperationException f) {
      e.addSuppressed(f);
      throw new IllegalStateException("Could not create ToolResponseMessage", e);
    }
  }

  private static void invokeBuilder(
      Object builder, String methodName, Class<?> parameterType, Object value)
      throws ReflectiveOperationException {
    builder.getClass().getMethod(methodName, parameterType).invoke(builder, value);
  }

  private static Map<String, Object> emptyMetadata() {
    return emptyMap();
  }

  private static AssistantMessage.ToolCall toolCall() {
    return new AssistantMessage.ToolCall(TOOL_CALL_ID, "function", TOOL_NAME, TOOL_ARGUMENTS);
  }

  private static AssistantMessage.ToolCall toolCall(
      String id, String type, String name, String arguments) {
    return new AssistantMessage.ToolCall(id, type, name, arguments);
  }

  private static ToolResponseMessage.ToolResponse toolResponse() {
    return new ToolResponseMessage.ToolResponse(TOOL_CALL_ID, TOOL_NAME, TOOL_RESPONSE);
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
