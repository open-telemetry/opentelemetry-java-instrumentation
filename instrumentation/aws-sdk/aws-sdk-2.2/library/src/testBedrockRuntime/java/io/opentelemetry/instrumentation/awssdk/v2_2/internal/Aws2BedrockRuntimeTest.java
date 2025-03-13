/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_FINISH_REASONS;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_SYSTEM;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_TOKEN_TYPE;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_INPUT_TOKENS;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_OUTPUT_TOKENS;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiSystemIncubatingValues.AWS_BEDROCK;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.KeyValue;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.awssdk.v2_2.AbstractAws2BedrockRuntimeTest;
import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.protocols.json.internal.unmarshall.document.DocumentUnmarshaller;
import software.amazon.awssdk.protocols.jsoncore.JsonNode;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClientBuilder;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClientBuilder;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlockStart;

class Aws2BedrockRuntimeTest extends AbstractAws2BedrockRuntimeTest {

  @RegisterExtension
  private static final LibraryInstrumentationExtension testing =
      LibraryInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }

  private static AwsSdkTelemetry telemetry;

  @BeforeAll
  static void setup() {
    telemetry =
        AwsSdkTelemetry.builder(testing.getOpenTelemetry())
            .setGenaiCaptureMessageContent(true)
            .build();
  }

  @Override
  protected ClientOverrideConfiguration.Builder createOverrideConfigurationBuilder() {
    return ClientOverrideConfiguration.builder()
        .addExecutionInterceptor(telemetry.newExecutionInterceptor());
  }

  @Override
  protected BedrockRuntimeAsyncClient configureBedrockRuntimeClient(
      BedrockRuntimeAsyncClient client) {
    return telemetry.wrapBedrockRuntimeClient(client);
  }

  @Test
  void testConverseToolCallNoMessageContent() {
    BedrockRuntimeClientBuilder builder = BedrockRuntimeClient.builder();
    builder.overrideConfiguration(
        ClientOverrideConfiguration.builder()
            .addExecutionInterceptor(
                AwsSdkTelemetry.builder(testing.getOpenTelemetry())
                    .build()
                    .newExecutionInterceptor())
            .build());
    configureClient(builder);
    BedrockRuntimeClient client = builder.build();

    String modelId = "amazon.nova-micro-v1:0";
    List<Message> messages = new ArrayList<>();
    messages.add(
        Message.builder()
            .role(ConversationRole.USER)
            .content(
                ContentBlock.fromText("What is the weather in Seattle and San Francisco today?"))
            .build());
    ConverseResponse response0 =
        client.converse(
            ConverseRequest.builder()
                .modelId(modelId)
                .messages(messages)
                .toolConfig(currentWeatherToolConfig())
                .build());

    String seattleToolUseId0 = "";
    String sanFranciscoToolUseId0 = "";
    for (ContentBlock content : response0.output().message().content()) {
      if (content.toolUse() == null) {
        continue;
      }
      String toolUseId = content.toolUse().toolUseId();
      switch (content.toolUse().input().asMap().get("location").asString()) {
        case "Seattle":
          seattleToolUseId0 = toolUseId;
          break;
        case "San Francisco":
          sanFranciscoToolUseId0 = toolUseId;
          break;
        default:
          throw new IllegalArgumentException("Invalid tool use: " + content.toolUse());
      }
    }
    String seattleToolUseId = seattleToolUseId0;
    String sanFranciscoToolUseId = sanFranciscoToolUseId0;

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("chat amazon.nova-micro-v1:0")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfying(
                                equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                                equalTo(
                                    GEN_AI_OPERATION_NAME,
                                    GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues
                                        .CHAT),
                                equalTo(GEN_AI_REQUEST_MODEL, modelId),
                                equalTo(GEN_AI_USAGE_INPUT_TOKENS, 415),
                                equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 162),
                                equalTo(GEN_AI_RESPONSE_FINISH_REASONS, asList("tool_use")))));

    getTesting()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            metric ->
                metric
                    .hasName("gen_ai.client.token.usage")
                    .hasUnit("{token}")
                    .hasDescription("Measures number of input and output tokens used")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(415)
                                        .hasCount(1)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                                            equalTo(
                                                GEN_AI_TOKEN_TYPE,
                                                GenAiIncubatingAttributes
                                                    .GenAiTokenTypeIncubatingValues.INPUT),
                                            equalTo(
                                                GEN_AI_OPERATION_NAME,
                                                GenAiIncubatingAttributes
                                                    .GenAiOperationNameIncubatingValues.CHAT),
                                            equalTo(GEN_AI_REQUEST_MODEL, modelId)),
                                point ->
                                    point
                                        .hasSum(162)
                                        .hasCount(1)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                                            equalTo(
                                                GEN_AI_TOKEN_TYPE,
                                                GenAiIncubatingAttributes
                                                    .GenAiTokenTypeIncubatingValues.COMPLETION),
                                            equalTo(
                                                GEN_AI_OPERATION_NAME,
                                                GenAiIncubatingAttributes
                                                    .GenAiOperationNameIncubatingValues.CHAT),
                                            equalTo(GEN_AI_REQUEST_MODEL, modelId)))),
            metric ->
                metric
                    .hasName("gen_ai.client.operation.duration")
                    .hasUnit("s")
                    .hasDescription("GenAI operation duration")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSumGreaterThan(0.0)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                                            equalTo(
                                                GEN_AI_OPERATION_NAME,
                                                GenAiIncubatingAttributes
                                                    .GenAiOperationNameIncubatingValues.CHAT),
                                            equalTo(GEN_AI_REQUEST_MODEL, modelId)))));

    SpanContext spanCtx0 = getTesting().waitForTraces(1).get(0).get(0).getSpanContext();

    getTesting()
        .waitAndAssertLogRecords(
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                        equalTo(EVENT_NAME, "gen_ai.user.message"))
                    .hasSpanContext(spanCtx0)
                    .hasBody(Value.of(Collections.emptyMap())),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK), equalTo(EVENT_NAME, "gen_ai.choice"))
                    .hasSpanContext(spanCtx0)
                    .hasBody(
                        Value.of(
                            KeyValue.of("finish_reason", Value.of("tool_use")),
                            KeyValue.of("index", Value.of(0)),
                            KeyValue.of(
                                "toolCalls",
                                Value.of(
                                    Value.of(
                                        KeyValue.of("name", Value.of("get_current_weather")),
                                        KeyValue.of("id", Value.of(seattleToolUseId)),
                                        KeyValue.of("type", Value.of("function"))),
                                    Value.of(
                                        KeyValue.of("name", Value.of("get_current_weather")),
                                        KeyValue.of("id", Value.of(sanFranciscoToolUseId)),
                                        KeyValue.of("type", Value.of("function"))))))));

    getTesting().clearData();

    messages.add(response0.output().message());
    messages.add(
        Message.builder()
            .role(ConversationRole.USER)
            .content(
                ContentBlock.fromToolResult(
                    ToolResultBlock.builder()
                        .content(
                            ToolResultContentBlock.builder()
                                .json(
                                    Document.mapBuilder()
                                        .putString("weather", "50 degrees and raining")
                                        .build())
                                .build())
                        .toolUseId(seattleToolUseId)
                        .build()),
                ContentBlock.fromToolResult(
                    ToolResultBlock.builder()
                        .content(
                            ToolResultContentBlock.builder()
                                .json(
                                    Document.mapBuilder()
                                        .putString("weather", "70 degrees and sunny")
                                        .build())
                                .build())
                        .toolUseId(sanFranciscoToolUseId)
                        .build()))
            .build());

    ConverseResponse response1 =
        client.converse(
            ConverseRequest.builder()
                .modelId(modelId)
                .messages(messages)
                .toolConfig(currentWeatherToolConfig())
                .build());

    assertThat(response1.output().message().content().get(0).text())
        .contains(
            "The current weather in Seattle is 50 degrees and raining. "
                + "In San Francisco, the weather is 70 degrees and sunny.");

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("chat amazon.nova-micro-v1:0")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfying(
                                equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                                equalTo(
                                    GEN_AI_OPERATION_NAME,
                                    GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues
                                        .CHAT),
                                equalTo(GEN_AI_REQUEST_MODEL, modelId),
                                equalTo(GEN_AI_USAGE_INPUT_TOKENS, 554),
                                equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 57),
                                equalTo(GEN_AI_RESPONSE_FINISH_REASONS, asList("end_turn")))));

    getTesting()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            metric ->
                metric
                    .hasName("gen_ai.client.token.usage")
                    .hasUnit("{token}")
                    .hasDescription("Measures number of input and output tokens used")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(554)
                                        .hasCount(1)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                                            equalTo(
                                                GEN_AI_TOKEN_TYPE,
                                                GenAiIncubatingAttributes
                                                    .GenAiTokenTypeIncubatingValues.INPUT),
                                            equalTo(
                                                GEN_AI_OPERATION_NAME,
                                                GenAiIncubatingAttributes
                                                    .GenAiOperationNameIncubatingValues.CHAT),
                                            equalTo(GEN_AI_REQUEST_MODEL, modelId)),
                                point ->
                                    point
                                        .hasSum(57)
                                        .hasCount(1)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                                            equalTo(
                                                GEN_AI_TOKEN_TYPE,
                                                GenAiIncubatingAttributes
                                                    .GenAiTokenTypeIncubatingValues.COMPLETION),
                                            equalTo(
                                                GEN_AI_OPERATION_NAME,
                                                GenAiIncubatingAttributes
                                                    .GenAiOperationNameIncubatingValues.CHAT),
                                            equalTo(GEN_AI_REQUEST_MODEL, modelId)))),
            metric ->
                metric
                    .hasName("gen_ai.client.operation.duration")
                    .hasUnit("s")
                    .hasDescription("GenAI operation duration")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSumGreaterThan(0.0)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                                            equalTo(
                                                GEN_AI_OPERATION_NAME,
                                                GenAiIncubatingAttributes
                                                    .GenAiOperationNameIncubatingValues.CHAT),
                                            equalTo(GEN_AI_REQUEST_MODEL, modelId)))));

    SpanContext spanCtx1 = getTesting().waitForTraces(1).get(0).get(0).getSpanContext();

    getTesting()
        .waitAndAssertLogRecords(
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                        equalTo(EVENT_NAME, "gen_ai.user.message"))
                    .hasSpanContext(spanCtx1)
                    .hasBody(Value.of(emptyMap())),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                        equalTo(EVENT_NAME, "gen_ai.assistant.message"))
                    .hasSpanContext(spanCtx1)
                    .hasBody(
                        Value.of(
                            KeyValue.of(
                                "toolCalls",
                                Value.of(
                                    Value.of(
                                        KeyValue.of("name", Value.of("get_current_weather")),
                                        KeyValue.of("id", Value.of(seattleToolUseId)),
                                        KeyValue.of("type", Value.of("function"))),
                                    Value.of(
                                        KeyValue.of("name", Value.of("get_current_weather")),
                                        KeyValue.of("id", Value.of(sanFranciscoToolUseId)),
                                        KeyValue.of("type", Value.of("function"))))))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                        equalTo(EVENT_NAME, "gen_ai.tool.message"))
                    .hasSpanContext(spanCtx1)
                    .hasBody(Value.of(KeyValue.of("id", Value.of(seattleToolUseId)))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                        equalTo(EVENT_NAME, "gen_ai.tool.message"))
                    .hasSpanContext(spanCtx1)
                    .hasBody(Value.of(KeyValue.of("id", Value.of(sanFranciscoToolUseId)))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK), equalTo(EVENT_NAME, "gen_ai.choice"))
                    .hasSpanContext(spanCtx1)
                    .hasBody(
                        Value.of(
                            KeyValue.of("finish_reason", Value.of("end_turn")),
                            KeyValue.of("index", Value.of(0)))));
  }

  @Test
  void testConverseToolCallStreamNoMessageContent()
      throws InterruptedException, ExecutionException {
    BedrockRuntimeAsyncClientBuilder builder = BedrockRuntimeAsyncClient.builder();
    AwsSdkTelemetry telemetry =
        AwsSdkTelemetry.builder(testing.getOpenTelemetry())
            .setGenaiCaptureMessageContent(false)
            .build();
    builder.overrideConfiguration(
        ClientOverrideConfiguration.builder()
            .addExecutionInterceptor(telemetry.newExecutionInterceptor())
            .build());
    configureClient(builder);
    BedrockRuntimeAsyncClient client = telemetry.wrapBedrockRuntimeClient(builder.build());

    String modelId = "amazon.nova-micro-v1:0";
    List<Message> messages = new ArrayList<>();
    messages.add(
        Message.builder()
            .role(ConversationRole.USER)
            .content(
                ContentBlock.fromText("What is the weather in Seattle and San Francisco today?"))
            .build());

    StringBuilder responseChunksText = new StringBuilder();
    List<ToolUseBlock.Builder> responseChunksTools = new ArrayList<>();
    StringBuilder currentToolArgs = new StringBuilder();

    ConverseStreamResponseHandler responseHandler =
        ConverseStreamResponseHandler.builder()
            .subscriber(
                ConverseStreamResponseHandler.Visitor.builder()
                    .onContentBlockStart(
                        chunk -> {
                          if (!responseChunksTools.isEmpty()) {
                            JsonNode node = JsonNode.parser().parse(currentToolArgs.toString());
                            currentToolArgs.setLength(0);
                            Document document =
                                node.visit(
                                    new software.amazon.awssdk.protocols.json.internal.unmarshall
                                        .document.DocumentUnmarshaller());
                            responseChunksTools.get(responseChunksTools.size() - 1).input(document);
                          }
                          ToolUseBlockStart toolUse = chunk.start().toolUse();
                          if (toolUse != null) {
                            responseChunksTools.add(
                                ToolUseBlock.builder()
                                    .name(toolUse.name())
                                    .toolUseId(toolUse.toolUseId()));
                          }
                        })
                    .onContentBlockDelta(
                        chunk -> {
                          ToolUseBlockDelta toolUse = chunk.delta().toolUse();
                          if (toolUse != null) {
                            currentToolArgs.append(toolUse.input());
                          }
                          String text = chunk.delta().text();
                          if (text != null) {
                            responseChunksText.append(text);
                          }
                        })
                    .build())
            .build();

    client
        .converseStream(
            ConverseStreamRequest.builder()
                .modelId(modelId)
                .messages(messages)
                .toolConfig(currentWeatherToolConfig())
                .build(),
            responseHandler)
        .get();

    if (currentToolArgs.length() > 0 && !responseChunksTools.isEmpty()) {
      JsonNode node = JsonNode.parser().parse(currentToolArgs.toString());
      currentToolArgs.setLength(0);
      Document document = node.visit(new DocumentUnmarshaller());
      responseChunksTools.get(responseChunksTools.size() - 1).input(document);
    }

    List<ToolUseBlock> toolUses =
        responseChunksTools.stream().map(ToolUseBlock.Builder::build).collect(Collectors.toList());

    String seattleToolUseId0 = "";
    String sanFranciscoToolUseId0 = "";
    for (ToolUseBlock toolUse : toolUses) {
      String toolUseId = toolUse.toolUseId();
      switch (toolUse.input().asMap().get("location").asString()) {
        case "Seattle":
          seattleToolUseId0 = toolUseId;
          break;
        case "San Francisco":
          sanFranciscoToolUseId0 = toolUseId;
          break;
        default:
          throw new IllegalArgumentException("Invalid tool use: " + toolUse);
      }
    }
    String seattleToolUseId = seattleToolUseId0;
    String sanFranciscoToolUseId = sanFranciscoToolUseId0;

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("chat amazon.nova-micro-v1:0")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfying(
                                equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                                equalTo(
                                    GEN_AI_OPERATION_NAME,
                                    GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues
                                        .CHAT),
                                equalTo(GEN_AI_REQUEST_MODEL, modelId),
                                equalTo(GEN_AI_USAGE_INPUT_TOKENS, 415),
                                equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 162),
                                equalTo(GEN_AI_RESPONSE_FINISH_REASONS, asList("tool_use")))));

    getTesting()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            metric ->
                metric
                    .hasName("gen_ai.client.token.usage")
                    .hasUnit("{token}")
                    .hasDescription("Measures number of input and output tokens used")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(415)
                                        .hasCount(1)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                                            equalTo(
                                                GEN_AI_TOKEN_TYPE,
                                                GenAiIncubatingAttributes
                                                    .GenAiTokenTypeIncubatingValues.INPUT),
                                            equalTo(
                                                GEN_AI_OPERATION_NAME,
                                                GenAiIncubatingAttributes
                                                    .GenAiOperationNameIncubatingValues.CHAT),
                                            equalTo(GEN_AI_REQUEST_MODEL, modelId)),
                                point ->
                                    point
                                        .hasSum(162)
                                        .hasCount(1)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                                            equalTo(
                                                GEN_AI_TOKEN_TYPE,
                                                GenAiIncubatingAttributes
                                                    .GenAiTokenTypeIncubatingValues.COMPLETION),
                                            equalTo(
                                                GEN_AI_OPERATION_NAME,
                                                GenAiIncubatingAttributes
                                                    .GenAiOperationNameIncubatingValues.CHAT),
                                            equalTo(GEN_AI_REQUEST_MODEL, modelId)))),
            metric ->
                metric
                    .hasName("gen_ai.client.operation.duration")
                    .hasUnit("s")
                    .hasDescription("GenAI operation duration")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSumGreaterThan(0.0)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                                            equalTo(
                                                GEN_AI_OPERATION_NAME,
                                                GenAiIncubatingAttributes
                                                    .GenAiOperationNameIncubatingValues.CHAT),
                                            equalTo(GEN_AI_REQUEST_MODEL, modelId)))));

    SpanContext spanCtx0 = getTesting().waitForTraces(1).get(0).get(0).getSpanContext();

    getTesting()
        .waitAndAssertLogRecords(
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                        equalTo(EVENT_NAME, "gen_ai.user.message"))
                    .hasSpanContext(spanCtx0)
                    .hasBody(Value.of(Collections.emptyMap())),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK), equalTo(EVENT_NAME, "gen_ai.choice"))
                    .hasSpanContext(spanCtx0)
                    .hasBody(
                        Value.of(
                            KeyValue.of("finish_reason", Value.of("tool_use")),
                            KeyValue.of("index", Value.of(0)),
                            KeyValue.of(
                                "toolCalls",
                                Value.of(
                                    Value.of(
                                        KeyValue.of("name", Value.of("get_current_weather")),
                                        KeyValue.of("id", Value.of(seattleToolUseId)),
                                        KeyValue.of("type", Value.of("function"))),
                                    Value.of(
                                        KeyValue.of("name", Value.of("get_current_weather")),
                                        KeyValue.of("id", Value.of(sanFranciscoToolUseId)),
                                        KeyValue.of("type", Value.of("function"))))))));

    getTesting().clearData();

    List<ContentBlock> contentBlocks = new ArrayList<>();
    contentBlocks.add(ContentBlock.fromText(responseChunksText.toString()));
    toolUses.stream()
        .map(toolUse -> ContentBlock.builder().toolUse(toolUse).build())
        .forEach(contentBlocks::add);
    messages.add(Message.builder().role(ConversationRole.ASSISTANT).content(contentBlocks).build());
    messages.add(
        Message.builder()
            .role(ConversationRole.USER)
            .content(
                ContentBlock.fromToolResult(
                    ToolResultBlock.builder()
                        .content(
                            ToolResultContentBlock.builder()
                                .json(
                                    Document.mapBuilder()
                                        .putString("weather", "50 degrees and raining")
                                        .build())
                                .build())
                        .toolUseId(seattleToolUseId)
                        .build()),
                ContentBlock.fromToolResult(
                    ToolResultBlock.builder()
                        .content(
                            ToolResultContentBlock.builder()
                                .json(
                                    Document.mapBuilder()
                                        .putString("weather", "70 degrees and sunny")
                                        .build())
                                .build())
                        .toolUseId(sanFranciscoToolUseId)
                        .build()))
            .build());

    List<String> responseChunks = new ArrayList<>();
    ConverseStreamResponseHandler responseHandler1 =
        ConverseStreamResponseHandler.builder()
            .subscriber(
                ConverseStreamResponseHandler.Visitor.builder()
                    .onContentBlockDelta(
                        chunk -> {
                          responseChunks.add(chunk.delta().text());
                        })
                    .build())
            .build();

    client
        .converseStream(
            ConverseStreamRequest.builder()
                .modelId(modelId)
                .messages(messages)
                .toolConfig(currentWeatherToolConfig())
                .build(),
            responseHandler1)
        .get();

    assertThat(String.join("", responseChunks))
        .contains(
            "The current weather in Seattle is 50 degrees and it is raining. "
                + "In San Francisco, the weather is 70 degrees and sunny.");

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("chat amazon.nova-micro-v1:0")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfying(
                                equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                                equalTo(
                                    GEN_AI_OPERATION_NAME,
                                    GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues
                                        .CHAT),
                                equalTo(GEN_AI_REQUEST_MODEL, modelId),
                                equalTo(GEN_AI_USAGE_INPUT_TOKENS, 554),
                                equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 59),
                                equalTo(GEN_AI_RESPONSE_FINISH_REASONS, asList("end_turn")))));

    getTesting()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            metric ->
                metric
                    .hasName("gen_ai.client.token.usage")
                    .hasUnit("{token}")
                    .hasDescription("Measures number of input and output tokens used")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(554)
                                        .hasCount(1)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                                            equalTo(
                                                GEN_AI_TOKEN_TYPE,
                                                GenAiIncubatingAttributes
                                                    .GenAiTokenTypeIncubatingValues.INPUT),
                                            equalTo(
                                                GEN_AI_OPERATION_NAME,
                                                GenAiIncubatingAttributes
                                                    .GenAiOperationNameIncubatingValues.CHAT),
                                            equalTo(GEN_AI_REQUEST_MODEL, modelId)),
                                point ->
                                    point
                                        .hasSum(59)
                                        .hasCount(1)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                                            equalTo(
                                                GEN_AI_TOKEN_TYPE,
                                                GenAiIncubatingAttributes
                                                    .GenAiTokenTypeIncubatingValues.COMPLETION),
                                            equalTo(
                                                GEN_AI_OPERATION_NAME,
                                                GenAiIncubatingAttributes
                                                    .GenAiOperationNameIncubatingValues.CHAT),
                                            equalTo(GEN_AI_REQUEST_MODEL, modelId)))),
            metric ->
                metric
                    .hasName("gen_ai.client.operation.duration")
                    .hasUnit("s")
                    .hasDescription("GenAI operation duration")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSumGreaterThan(0.0)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                                            equalTo(
                                                GEN_AI_OPERATION_NAME,
                                                GenAiIncubatingAttributes
                                                    .GenAiOperationNameIncubatingValues.CHAT),
                                            equalTo(GEN_AI_REQUEST_MODEL, modelId)))));

    SpanContext spanCtx1 = getTesting().waitForTraces(1).get(0).get(0).getSpanContext();

    getTesting()
        .waitAndAssertLogRecords(
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                        equalTo(EVENT_NAME, "gen_ai.user.message"))
                    .hasSpanContext(spanCtx1)
                    .hasBody(Value.of(emptyMap())),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                        equalTo(EVENT_NAME, "gen_ai.assistant.message"))
                    .hasSpanContext(spanCtx1)
                    .hasBody(
                        Value.of(
                            KeyValue.of(
                                "toolCalls",
                                Value.of(
                                    Value.of(
                                        KeyValue.of("name", Value.of("get_current_weather")),
                                        KeyValue.of("id", Value.of(seattleToolUseId)),
                                        KeyValue.of("type", Value.of("function"))),
                                    Value.of(
                                        KeyValue.of("name", Value.of("get_current_weather")),
                                        KeyValue.of("id", Value.of(sanFranciscoToolUseId)),
                                        KeyValue.of("type", Value.of("function"))))))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                        equalTo(EVENT_NAME, "gen_ai.tool.message"))
                    .hasSpanContext(spanCtx1)
                    .hasBody(Value.of(KeyValue.of("id", Value.of(seattleToolUseId)))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                        equalTo(EVENT_NAME, "gen_ai.tool.message"))
                    .hasSpanContext(spanCtx1)
                    .hasBody(Value.of(KeyValue.of("id", Value.of(sanFranciscoToolUseId)))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK), equalTo(EVENT_NAME, "gen_ai.choice"))
                    .hasSpanContext(spanCtx1)
                    .hasBody(
                        Value.of(
                            KeyValue.of("finish_reason", Value.of("end_turn")),
                            KeyValue.of("index", Value.of(0)))));
  }

  private static ToolConfiguration currentWeatherToolConfig() {
    return ToolConfiguration.builder()
        .tools(
            Tool.builder()
                .toolSpec(
                    ToolSpecification.builder()
                        .name("get_current_weather")
                        .description("Get the current weather in a given location.")
                        .inputSchema(
                            ToolInputSchema.builder()
                                .json(
                                    Document.mapBuilder()
                                        .putString("type", "object")
                                        .putDocument(
                                            "properties",
                                            Document.mapBuilder()
                                                .putDocument(
                                                    "location",
                                                    Document.mapBuilder()
                                                        .putString("type", "string")
                                                        .putString(
                                                            "description", "The name of the city")
                                                        .build())
                                                .build())
                                        .putList(
                                            "required",
                                            singletonList(Document.fromString("location")))
                                        .build())
                                .build())
                        .build())
                .build())
        .build();
  }
}
