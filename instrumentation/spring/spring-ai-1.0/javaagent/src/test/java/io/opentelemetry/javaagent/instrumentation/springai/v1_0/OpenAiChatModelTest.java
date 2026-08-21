/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springai.v1_0;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
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
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_TOKEN_TYPE;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_INPUT_TOKENS;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_OUTPUT_TOKENS;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues.CHAT;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiProviderNameIncubatingValues.OPENAI;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiTokenTypeIncubatingValues.INPUT;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiTokenTypeIncubatingValues.OUTPUT;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.opentelemetry.api.common.KeyValue;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.testing.common.TestAgentListenerAccess;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@SuppressWarnings("OtelDeprecatedApiUsage")
class OpenAiChatModelTest {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-ai-1.0";
  private static final String MODEL = "gpt-4o-mini";
  private static final String RESPONSE_MODEL = "gpt-4o-mini-2024-07-18";
  private static final String PROMPT =
      "Answer in up to 3 words: Which ocean contains Bouvet Island?";
  private static final String RESPONSE = "Atlantic Ocean";
  private static final String FINISH_REASON = "STOP";
  private static final boolean CAPTURE_MESSAGE_CONTENT =
      Boolean.getBoolean("otel.instrumentation.genai.capture-message-content");
  private static final boolean EXPERIMENTAL_ATTRIBUTES =
      Boolean.getBoolean(
          "otel.instrumentation.spring-ai.experimental.capture-message-content-as-span-attributes.enabled");

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  static final WireMockExtension openAi =
      WireMockExtension.newInstance()
          .options(options().dynamicPort().usingFilesUnderClasspath("wiremock"))
          .build();

  @BeforeAll
  static void setUp() {
    TestAgentListenerAccess.addSkipTransformationCondition(
        typeName ->
            typeName != null
                && (typeName.startsWith("org.springframework.web.client.")
                    || typeName.startsWith("org.springframework.http.client.")));
  }

  @Test
  void callRecordsOpenAiSpringAiResponse() {
    OpenAiChatModel chatModel = openAiChatModel();

    ChatResponse response = testing.runWithSpan("parent", () -> chatModel.call(new Prompt(PROMPT)));

    assertThat(response.getResult().getOutput().getText()).isEqualTo(RESPONSE);
    List<LoggedRequest> requests =
        openAi.findAll(postRequestedFor(urlEqualTo("/chat/completions")));
    assertThat(requests).hasSize(1);
    assertThat(requests.get(0).getBodyAsString()).contains(MODEL, PROMPT);

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
                            equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                            equalTo(GEN_AI_OPERATION_NAME, CHAT),
                            equalTo(GEN_AI_REQUEST_MODEL, MODEL),
                            equalTo(GEN_AI_RESPONSE_FINISH_REASONS, singletonList(FINISH_REASON)),
                            equalTo(GEN_AI_RESPONSE_ID, "chatcmpl-test"),
                            equalTo(GEN_AI_RESPONSE_MODEL, RESPONSE_MODEL),
                            equalTo(GEN_AI_USAGE_INPUT_TOKENS, 22L),
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
                                        + "\"}],\"finish_reason\":\""
                                        + FINISH_REASON
                                        + "\"}]")))));
    assertMetrics();
    assertMessageEvents(spanContext);
  }

  private static OpenAiChatModel openAiChatModel() {
    OpenAiApi openAiApi =
        OpenAiApi.builder()
            .baseUrl(openAi.baseUrl())
            .apiKey("unused")
            .completionsPath("/chat/completions")
            .restClientBuilder(
                RestClient.builder().requestFactory(new SimpleClientHttpRequestFactory()))
            .build();
    return OpenAiChatModel.builder()
        .openAiApi(openAiApi)
        .defaultOptions(OpenAiChatOptions.builder().model(MODEL).build())
        .build();
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
                                        equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                                        equalTo(GEN_AI_OPERATION_NAME, CHAT),
                                        equalTo(GEN_AI_REQUEST_MODEL, MODEL),
                                        equalTo(GEN_AI_RESPONSE_MODEL, RESPONSE_MODEL)))),
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
                                        equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                                        equalTo(GEN_AI_OPERATION_NAME, CHAT),
                                        equalTo(GEN_AI_REQUEST_MODEL, MODEL),
                                        equalTo(GEN_AI_RESPONSE_MODEL, RESPONSE_MODEL),
                                        equalTo(GEN_AI_TOKEN_TYPE, INPUT)),
                            point ->
                                point
                                    .hasSum(2.0)
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                                        equalTo(GEN_AI_OPERATION_NAME, CHAT),
                                        equalTo(GEN_AI_REQUEST_MODEL, MODEL),
                                        equalTo(GEN_AI_RESPONSE_MODEL, RESPONSE_MODEL),
                                        equalTo(GEN_AI_TOKEN_TYPE, OUTPUT)))));
  }

  private static void assertMessageEvents(SpanContext spanContext) {
    testing.waitAndAssertLogRecords(
        log ->
            log.hasAttributesSatisfyingExactly(
                    equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                    equalTo(stringKey("event.name"), "gen_ai.user.message"))
                .hasSpanContext(spanContext)
                .hasBody(messageBody(PROMPT)),
        log ->
            log.hasAttributesSatisfyingExactly(
                    equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                    equalTo(stringKey("event.name"), "gen_ai.choice"))
                .hasSpanContext(spanContext)
                .hasBody(choiceBody()));
  }

  private static Value<?> messageBody(String content) {
    return CAPTURE_MESSAGE_CONTENT
        ? Value.of(KeyValue.of("content", Value.of(content)))
        : Value.of(emptyMap());
  }

  private static Value<?> choiceBody() {
    Value<?> message =
        CAPTURE_MESSAGE_CONTENT
            ? Value.of(KeyValue.of("content", Value.of(RESPONSE)))
            : Value.of(emptyMap());
    return Value.of(
        KeyValue.of("finish_reason", Value.of(FINISH_REASON)),
        KeyValue.of("index", Value.of(0)),
        KeyValue.of("message", message));
  }

  private static <T> T messageSpanAttribute(T value) {
    return EXPERIMENTAL_ATTRIBUTES ? value : null;
  }
}
