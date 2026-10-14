/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.EventIncubatingAttributes.EVENT_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_PROVIDER_NAME;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

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
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

// TODO: Remove after https://github.com/open-telemetry/semantic-conventions-genai/issues/247
// is resolved.
@SuppressWarnings("deprecation")
class BedrockRuntimeEventsTest {

  @ParameterizedTest
  @MethodSource("messages")
  void exportsMessageBody(Message message, boolean captureMessageContent, Value<?> expectedBody) {
    InMemoryLogRecordExporter exporter = InMemoryLogRecordExporter.create();
    try (SdkLoggerProvider loggerProvider =
        SdkLoggerProvider.builder()
            .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
            .build()) {
      BedrockRuntimeImpl.recordRequestEvents(
          Context.root(),
          loggerProvider.get("test"),
          new ExecutionAttributes(),
          ConverseRequest.builder().messages(message).build(),
          captureMessageContent);

      assertThat(exporter.getFinishedLogRecordItems())
          .singleElement()
          .satisfies(
              log ->
                  assertThat(log)
                      .hasAttributesSatisfyingExactly(
                          equalTo(EVENT_NAME, "gen_ai." + message.roleAsString() + ".message"),
                          equalTo(GEN_AI_PROVIDER_NAME, "aws.bedrock"))
                      .hasBody(expectedBody));
    }
  }

  private static Stream<Arguments> messages() {
    return Stream.of(
        argumentSet(
            "user content excluded",
            Message.builder().role("user").content(ContentBlock.fromText("hello")).build(),
            false,
            null),
        argumentSet(
            "assistant content excluded",
            Message.builder().role("assistant").content(ContentBlock.fromText("hello")).build(),
            false,
            null),
        argumentSet("no content available", Message.builder().role("user").build(), true, null),
        argumentSet(
            "captured empty content",
            Message.builder().role("user").content(ContentBlock.fromText("")).build(),
            true,
            Value.of(KeyValue.of("content", Value.of("")))),
        argumentSet(
            "captured content",
            Message.builder().role("user").content(ContentBlock.fromText("hello")).build(),
            true,
            Value.of(KeyValue.of("content", Value.of("hello")))));
  }
}
