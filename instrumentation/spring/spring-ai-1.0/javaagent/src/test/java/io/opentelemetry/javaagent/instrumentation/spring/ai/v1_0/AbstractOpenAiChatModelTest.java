/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.instrumentation.testing.util.InstrumentationScopeAssertions.hasScopeSchemaUrl;
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
import io.opentelemetry.semconv.SchemaUrls;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

@SuppressWarnings("deprecation") // using deprecated semconv
abstract class AbstractOpenAiChatModelTest {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-ai-1.0";
  private static final String MODEL = "gpt-4o-mini";
  private static final String RESPONSE_MODEL = "gpt-4o-mini-2024-07-18";
  private static final String PROMPT =
      "Answer in up to 3 words: Which ocean contains Bouvet Island?";
  private static final String RESPONSE = "Atlantic Ocean";
  private static final String FINISH_REASON = "STOP";
  private static final boolean CAPTURE_MESSAGE_CONTENT =
      Boolean.getBoolean("otel.instrumentation.genai.capture-message-content");

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  static final WireMockExtension openAi =
      WireMockExtension.newInstance()
          .options(options().dynamicPort().usingFilesUnderClasspath("wiremock"))
          .build();

  protected abstract ChatModel openAiChatModel(String baseUrl, String model);

  @Test
  void callRecordsOpenAiSpringAiResponse() {
    ChatModel chatModel = openAiChatModel(openAi.baseUrl(), MODEL);

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
                        .satisfies(hasScopeSchemaUrl(SchemaUrls.V1_37_0))
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
                            equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 2L)),
                // http client span
                span -> span.hasName("POST").hasKind(CLIENT).hasParent(trace.getSpan(1))));
    assertMetrics();
    assertMessageEvents(spanContext);
  }

  private static void assertMetrics() {
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metric ->
            metric
                .hasName("gen_ai.client.operation.duration")
                .satisfies(hasScopeSchemaUrl(SchemaUrls.V1_37_0))
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
                .satisfies(hasScopeSchemaUrl(SchemaUrls.V1_37_0))
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
                .satisfies(hasScopeSchemaUrl(SchemaUrls.V1_37_0))
                .hasBody(messageBody(PROMPT)),
        log ->
            log.hasAttributesSatisfyingExactly(
                    equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                    equalTo(stringKey("event.name"), "gen_ai.choice"))
                .hasSpanContext(spanContext)
                .satisfies(hasScopeSchemaUrl(SchemaUrls.V1_37_0))
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
}
