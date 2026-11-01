/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.EventIncubatingAttributes.EVENT_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_PROVIDER_NAME;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionDeveloperMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import io.opentelemetry.api.common.KeyValue;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("deprecation") // using deprecated semconv
class ChatCompletionEventsHelperTest {

  @ParameterizedTest
  @MethodSource("messages")
  void exportsPromptBody(
      ChatCompletionMessageParam message,
      boolean captureMessageContent,
      String eventName,
      Value<?> expectedBody) {
    InMemoryLogRecordExporter exporter = InMemoryLogRecordExporter.create();
    try (SdkLoggerProvider loggerProvider =
        SdkLoggerProvider.builder()
            .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
            .build()) {
      ChatCompletionEventsHelper.emitPromptLogEvents(
          Context.root(),
          loggerProvider.get("test"),
          ChatCompletionCreateParams.builder().model("test").addMessage(message).build(),
          captureMessageContent);

      assertThat(exporter.getFinishedLogRecordItems())
          .singleElement()
          .satisfies(
              log ->
                  assertThat(log)
                      .hasAttributesSatisfyingExactly(
                          equalTo(EVENT_NAME, eventName), equalTo(GEN_AI_PROVIDER_NAME, "openai"))
                      .hasBody(expectedBody));
    }
  }

  private static Stream<Arguments> messages() {
    return Stream.of(
        argumentSet(
            "assistant content excluded",
            ChatCompletionMessageParam.ofAssistant(
                ChatCompletionAssistantMessageParam.builder().content("hello").build()),
            false,
            "gen_ai.assistant.message",
            null),
        argumentSet(
            "assistant without content",
            ChatCompletionMessageParam.ofAssistant(
                ChatCompletionAssistantMessageParam.builder().build()),
            true,
            "gen_ai.assistant.message",
            null),
        argumentSet(
            "non-default role retained",
            ChatCompletionMessageParam.ofDeveloper(
                ChatCompletionDeveloperMessageParam.builder().content("hello").build()),
            false,
            "gen_ai.system.message",
            Value.of(KeyValue.of("role", Value.of("developer")))),
        argumentSet(
            "captured empty content",
            ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder().content("").build()),
            true,
            "gen_ai.user.message",
            Value.of(KeyValue.of("content", Value.of("")))));
  }
}
