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
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.protocols.json.SdkJsonGenerator;
import software.amazon.awssdk.protocols.json.internal.marshall.DocumentTypeJsonMarshaller;
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
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.PayloadPart;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlockStart;
import software.amazon.awssdk.thirdparty.jackson.core.JsonFactory;

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
                    .hasDescription("Measures number of input and output tokens used.")
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
                    .hasDescription("GenAI operation duration.")
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

    // Clear to allow asserting telemetry of user request and tool result processing separately.
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
                    .hasDescription("Measures number of input and output tokens used.")
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
                    .hasDescription("GenAI operation duration.")
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
                            Document document = node.visit(new DocumentUnmarshaller());
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
                    .hasDescription("Measures number of input and output tokens used.")
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
                    .hasDescription("GenAI operation duration.")
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

    // Clear to allow asserting telemetry of user request and tool result processing separately.
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
                    .hasDescription("Measures number of input and output tokens used.")
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
                    .hasDescription("GenAI operation duration.")
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

  @Test
  void testInvokeModelToolCallAmazonNovaNoMessageContent() {
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

    Document toolConfig =
        Document.mapBuilder()
            .putList(
                "tools",
                singletonList(
                    Document.mapBuilder()
                        .putDocument(
                            "toolSpec",
                            Document.mapBuilder()
                                .putString("name", "get_current_weather")
                                .putString(
                                    "description", "Get the current weather in a given location.")
                                .putDocument(
                                    "inputSchema",
                                    Document.mapBuilder()
                                        .putDocument(
                                            "json",
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
                                                                    "description",
                                                                    "The name of the city")
                                                                .build())
                                                        .build())
                                                .putList(
                                                    "required",
                                                    singletonList(Document.fromString("location")))
                                                .build())
                                        .build())
                                .build())
                        .build()))
            .build();

    List<Document> messages = new ArrayList<>();
    messages.add(
        Document.mapBuilder()
            .putString("role", "user")
            .putList(
                "content",
                singletonList(
                    Document.mapBuilder()
                        .putString(
                            "text", "What is the weather in Seattle and San Francisco today?")
                        .build()))
            .build());

    Document requestPayload0 =
        Document.mapBuilder()
            .putList("messages", messages)
            .putDocument("toolConfig", toolConfig)
            .build();

    SdkJsonGenerator generator0 = new SdkJsonGenerator(new JsonFactory(), "application/json");
    DocumentTypeJsonMarshaller marshaller0 = new DocumentTypeJsonMarshaller(generator0);
    requestPayload0.accept(marshaller0);

    InvokeModelRequest request0 =
        InvokeModelRequest.builder()
            .modelId(modelId)
            .body(SdkBytes.fromByteArray(generator0.getBytes()))
            .build();

    InvokeModelResponse response0 = client.invokeModel(request0);

    JsonNode node0 = JsonNode.parser().parse(response0.body().asByteArray());
    Document responsePayload0 = node0.visit(new DocumentUnmarshaller());

    Document message = responsePayload0.asMap().get("output").asMap().get("message");

    String seattleToolUseId0 = "";
    String sanFranciscoToolUseId0 = "";
    for (Document content : message.asMap().get("content").asList()) {
      Document toolUse = content.asMap().get("toolUse");
      if (toolUse == null) {
        continue;
      }
      String toolUseId = toolUse.asMap().get("toolUseId").asString();
      switch (toolUse.asMap().get("input").asMap().get("location").asString()) {
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
                                equalTo(GEN_AI_USAGE_INPUT_TOKENS, 416),
                                equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 166),
                                equalTo(GEN_AI_RESPONSE_FINISH_REASONS, asList("tool_use")))));

    getTesting()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            metric ->
                metric
                    .hasName("gen_ai.client.token.usage")
                    .hasUnit("{token}")
                    .hasDescription("Measures number of input and output tokens used.")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(416)
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
                                        .hasSum(166)
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
                    .hasDescription("GenAI operation duration.")
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

    // Clear to allow asserting telemetry of user request and tool result processing separately.
    getTesting().clearData();

    messages.add(message);
    messages.add(
        Document.mapBuilder()
            .putString("role", "user")
            .putList(
                "content",
                asList(
                    Document.mapBuilder()
                        .putDocument(
                            "toolResult",
                            Document.mapBuilder()
                                .putString("toolUseId", seattleToolUseId)
                                .putList(
                                    "content",
                                    singletonList(
                                        Document.mapBuilder()
                                            .putDocument(
                                                "json",
                                                Document.mapBuilder()
                                                    .putString("weather", "50 degrees and raining")
                                                    .build())
                                            .build()))
                                .build())
                        .build(),
                    Document.mapBuilder()
                        .putDocument(
                            "toolResult",
                            Document.mapBuilder()
                                .putString("toolUseId", sanFranciscoToolUseId)
                                .putList(
                                    "content",
                                    singletonList(
                                        Document.mapBuilder()
                                            .putDocument(
                                                "json",
                                                Document.mapBuilder()
                                                    .putString("weather", "70 degrees and sunny")
                                                    .build())
                                            .build()))
                                .build())
                        .build()))
            .build());

    Document requestPayload1 =
        Document.mapBuilder()
            .putList("messages", messages)
            .putDocument("toolConfig", toolConfig)
            .build();

    SdkJsonGenerator generator1 = new SdkJsonGenerator(new JsonFactory(), "application/json");
    DocumentTypeJsonMarshaller marshaller1 = new DocumentTypeJsonMarshaller(generator1);
    requestPayload1.accept(marshaller1);

    InvokeModelRequest request1 =
        InvokeModelRequest.builder()
            .modelId(modelId)
            .body(SdkBytes.fromByteArray(generator1.getBytes()))
            .build();

    InvokeModelResponse response1 = client.invokeModel(request1);

    JsonNode node1 = JsonNode.parser().parse(response1.body().asByteArray());
    Document responsePayload1 = node1.visit(new DocumentUnmarshaller());

    Document message1 = responsePayload1.asMap().get("output").asMap().get("message");

    assertThat(message1.asMap().get("content").asList().get(0).asMap().get("text").asString())
        .contains(
            "The current weather in Seattle is 50 degrees and it is raining. "
                + "The current weather in San Francisco is 70 degrees and it is sunny.");

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
                                equalTo(GEN_AI_USAGE_INPUT_TOKENS, 559),
                                equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 59),
                                equalTo(GEN_AI_RESPONSE_FINISH_REASONS, asList("end_turn")))));

    getTesting()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            metric ->
                metric
                    .hasName("gen_ai.client.token.usage")
                    .hasUnit("{token}")
                    .hasDescription("Measures number of input and output tokens used.")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(559)
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
                    .hasDescription("GenAI operation duration.")
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
                    .hasBody(Value.of(Collections.emptyMap())),
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
  void testInvokeModelWithResponseStreamToolCallAmazonNovaNoMessageContent()
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
    Document toolConfig =
        Document.mapBuilder()
            .putList(
                "tools",
                singletonList(
                    Document.mapBuilder()
                        .putDocument(
                            "toolSpec",
                            Document.mapBuilder()
                                .putString("name", "get_current_weather")
                                .putString(
                                    "description", "Get the current weather in a given location.")
                                .putDocument(
                                    "inputSchema",
                                    Document.mapBuilder()
                                        .putDocument(
                                            "json",
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
                                                                    "description",
                                                                    "The name of the city")
                                                                .build())
                                                        .build())
                                                .putList(
                                                    "required",
                                                    singletonList(Document.fromString("location")))
                                                .build())
                                        .build())
                                .build())
                        .build()))
            .build();

    List<Document> messages = new ArrayList<>();
    messages.add(
        Document.mapBuilder()
            .putString("role", "user")
            .putList(
                "content",
                singletonList(
                    Document.mapBuilder()
                        .putString(
                            "text", "What is the weather in Seattle and San Francisco today?")
                        .build()))
            .build());

    Document requestPayload0 =
        Document.mapBuilder()
            .putList("messages", messages)
            .putDocument("toolConfig", toolConfig)
            .build();

    SdkJsonGenerator generator0 = new SdkJsonGenerator(new JsonFactory(), "application/json");
    DocumentTypeJsonMarshaller marshaller0 = new DocumentTypeJsonMarshaller(generator0);
    requestPayload0.accept(marshaller0);

    InvokeModelWithResponseStreamRequest request0 =
        InvokeModelWithResponseStreamRequest.builder()
            .modelId(modelId)
            .body(SdkBytes.fromByteArray(generator0.getBytes()))
            .build();

    StringBuilder text = new StringBuilder();
    List<ToolUseBlock> toolCalls = new ArrayList<>();

    InvokeModelWithResponseStreamResponseHandler responseHandler0 =
        InvokeModelWithResponseStreamResponseHandler.builder()
            .subscriber(
                InvokeModelWithResponseStreamResponseHandler.Visitor.builder()
                    .onChunk(
                        new Consumer<PayloadPart>() {
                          private ToolUseBlock.Builder currentToolUse;

                          @Override
                          public void accept(PayloadPart chunk) {
                            JsonNode node = JsonNode.parser().parse(chunk.bytes().asByteArray());
                            DocumentUnmarshaller unmarshaller = new DocumentUnmarshaller();
                            Document result = node.visit(unmarshaller);

                            Document contentBlockStart = result.asMap().get("contentBlockStart");
                            if (contentBlockStart != null) {
                              Document toolUse =
                                  contentBlockStart.asMap().get("start").asMap().get("toolUse");
                              if (toolUse != null) {
                                currentToolUse = ToolUseBlock.builder();
                                Document toolUseId = toolUse.asMap().get("toolUseId");
                                if (toolUseId != null) {
                                  currentToolUse.toolUseId(toolUseId.asString());
                                }
                                Document name = toolUse.asMap().get("name");
                                if (name != null) {
                                  currentToolUse.name(name.asString());
                                }
                              }
                            }
                            Document contentBlockDelta = result.asMap().get("contentBlockDelta");
                            if (contentBlockDelta != null) {
                              Document delta = contentBlockDelta.asMap().get("delta");
                              Document t = delta.asMap().get("text");
                              if (t != null) {
                                text.append(t.asString());
                              }

                              Document toolUse = delta.asMap().get("toolUse");
                              if (toolUse != null) {
                                Document input = toolUse.asMap().get("input");
                                if (input != null) {
                                  JsonNode node1 = JsonNode.parser().parse(input.asString());
                                  DocumentUnmarshaller unmarshaller1 = new DocumentUnmarshaller();
                                  Document result1 = node1.visit(unmarshaller1);
                                  currentToolUse.input(result1);
                                }
                              }
                            }
                            if (result.asMap().get("contentBlockStop") != null
                                && currentToolUse != null) {
                              toolCalls.add(currentToolUse.build());
                              currentToolUse = null;
                            }
                          }
                        })
                    .build())
            .build();

    client.invokeModelWithResponseStream(request0, responseHandler0).get();

    String seattleToolUseId0 = "";
    String sanFranciscoToolUseId0 = "";
    for (ToolUseBlock toolUse : toolCalls) {
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
                                equalTo(GEN_AI_USAGE_INPUT_TOKENS, 416),
                                equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 165),
                                equalTo(GEN_AI_RESPONSE_FINISH_REASONS, asList("tool_use")))));

    getTesting()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            metric ->
                metric
                    .hasName("gen_ai.client.token.usage")
                    .hasUnit("{token}")
                    .hasDescription("Measures number of input and output tokens used.")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(416)
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
                                        .hasSum(165)
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
                    .hasDescription("GenAI operation duration.")
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

    // Clear to allow asserting telemetry of user request and tool result processing separately.
    getTesting().clearData();

    messages.add(
        Document.mapBuilder()
            .putString("role", "assistant")
            .putList(
                "content",
                asList(
                    Document.mapBuilder().putString("text", text.toString()).build(),
                    Document.mapBuilder()
                        .putDocument(
                            "toolUse",
                            Document.mapBuilder()
                                .putString("toolUseId", toolCalls.get(0).toolUseId())
                                .putString("name", toolCalls.get(0).name())
                                .putDocument("input", toolCalls.get(0).input())
                                .build())
                        .build(),
                    Document.mapBuilder()
                        .putDocument(
                            "toolUse",
                            Document.mapBuilder()
                                .putString("toolUseId", toolCalls.get(1).toolUseId())
                                .putString("name", toolCalls.get(1).name())
                                .putDocument("input", toolCalls.get(1).input())
                                .build())
                        .build()))
            .build());
    messages.add(
        Document.mapBuilder()
            .putString("role", "user")
            .putList(
                "content",
                asList(
                    Document.mapBuilder()
                        .putDocument(
                            "toolResult",
                            Document.mapBuilder()
                                .putString("toolUseId", seattleToolUseId)
                                .putList(
                                    "content",
                                    singletonList(
                                        Document.mapBuilder()
                                            .putDocument(
                                                "json",
                                                Document.mapBuilder()
                                                    .putString("weather", "50 degrees and raining")
                                                    .build())
                                            .build()))
                                .build())
                        .build(),
                    Document.mapBuilder()
                        .putDocument(
                            "toolResult",
                            Document.mapBuilder()
                                .putString("toolUseId", sanFranciscoToolUseId)
                                .putList(
                                    "content",
                                    singletonList(
                                        Document.mapBuilder()
                                            .putDocument(
                                                "json",
                                                Document.mapBuilder()
                                                    .putString("weather", "70 degrees and sunny")
                                                    .build())
                                            .build()))
                                .build())
                        .build()))
            .build());

    Document requestPayload1 =
        Document.mapBuilder()
            .putList("messages", messages)
            .putDocument("toolConfig", toolConfig)
            .build();

    SdkJsonGenerator generator1 = new SdkJsonGenerator(new JsonFactory(), "application/json");
    DocumentTypeJsonMarshaller marshaller1 = new DocumentTypeJsonMarshaller(generator1);
    requestPayload1.accept(marshaller1);

    InvokeModelWithResponseStreamRequest request1 =
        InvokeModelWithResponseStreamRequest.builder()
            .modelId(modelId)
            .body(SdkBytes.fromByteArray(generator1.getBytes()))
            .build();

    text.setLength(0);
    InvokeModelWithResponseStreamResponseHandler responseHandler1 =
        InvokeModelWithResponseStreamResponseHandler.builder()
            .subscriber(
                InvokeModelWithResponseStreamResponseHandler.Visitor.builder()
                    .onChunk(
                        chunk -> {
                          JsonNode node = JsonNode.parser().parse(chunk.bytes().asByteArray());
                          DocumentUnmarshaller unmarshaller = new DocumentUnmarshaller();
                          Document result = node.visit(unmarshaller);

                          Document contentBlockDelta = result.asMap().get("contentBlockDelta");
                          if (contentBlockDelta != null) {
                            Document delta = contentBlockDelta.asMap().get("delta");
                            Document t = delta.asMap().get("text");
                            if (t != null) {
                              text.append(t.asString());
                            }
                          }
                        })
                    .build())
            .build();

    client.invokeModelWithResponseStream(request1, responseHandler1).get();

    assertThat(text.toString())
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
                                equalTo(GEN_AI_USAGE_INPUT_TOKENS, 558),
                                equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 58),
                                equalTo(GEN_AI_RESPONSE_FINISH_REASONS, asList("end_turn")))));

    getTesting()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            metric ->
                metric
                    .hasName("gen_ai.client.token.usage")
                    .hasUnit("{token}")
                    .hasDescription("Measures number of input and output tokens used.")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(558)
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
                                        .hasSum(58)
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
                    .hasDescription("GenAI operation duration.")
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
                    .hasBody(Value.of(Collections.emptyMap())),
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
  void testInvokeModelToolCallAnthropicClaudeNoMessageContent() {
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

    String modelId = "anthropic.claude-3-5-sonnet-20240620-v1:0";
    Document tools =
        Document.listBuilder()
            .addDocument(
                Document.mapBuilder()
                    .putString("name", "get_current_weather")
                    .putString("description", "Get the current weather in a given location.")
                    .putDocument(
                        "input_schema",
                        Document.mapBuilder()
                            .putString("type", "object")
                            .putDocument(
                                "properties",
                                Document.mapBuilder()
                                    .putDocument(
                                        "location",
                                        Document.mapBuilder()
                                            .putString("type", "string")
                                            .putString("description", "The name of the city")
                                            .build())
                                    .build())
                            .putList("required", singletonList(Document.fromString("location")))
                            .build())
                    .build())
            .build();

    List<Document> messages = new ArrayList<>();
    messages.add(
        Document.mapBuilder()
            .putString("role", "user")
            .putList(
                "content",
                singletonList(
                    Document.mapBuilder()
                        .putString("type", "text")
                        .putString(
                            "text", "What is the weather in Seattle and San Francisco today?")
                        .build()))
            .build());

    Document requestPayload0 =
        Document.mapBuilder()
            .putString("anthropic_version", "bedrock-2023-05-31")
            .putNumber("max_tokens", 1000)
            .putList("messages", messages)
            .putDocument("tools", tools)
            .build();

    SdkJsonGenerator generator0 = new SdkJsonGenerator(new JsonFactory(), "application/json");
    DocumentTypeJsonMarshaller marshaller0 = new DocumentTypeJsonMarshaller(generator0);
    requestPayload0.accept(marshaller0);

    InvokeModelRequest request0 =
        InvokeModelRequest.builder()
            .modelId(modelId)
            .body(SdkBytes.fromByteArray(generator0.getBytes()))
            .build();

    InvokeModelResponse response0 = client.invokeModel(request0);

    JsonNode node0 = JsonNode.parser().parse(response0.body().asByteArray());
    Document message = node0.visit(new DocumentUnmarshaller());

    String seattleToolUseId0 = "";
    String sanFranciscoToolUseId0 = "";
    for (Document content : message.asMap().get("content").asList()) {
      Document type = content.asMap().get("type");
      if (type == null || !type.asString().equals("tool_use")) {
        continue;
      }
      String toolUseId = content.asMap().get("id").asString();
      switch (content.asMap().get("input").asMap().get("location").asString()) {
        case "Seattle":
          seattleToolUseId0 = toolUseId;
          break;
        case "San Francisco":
          sanFranciscoToolUseId0 = toolUseId;
          break;
        default:
          throw new IllegalArgumentException("Invalid tool use: " + content);
      }
    }
    String seattleToolUseId = seattleToolUseId0;
    String sanFranciscoToolUseId = sanFranciscoToolUseId0;

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("chat anthropic.claude-3-5-sonnet-20240620-v1:0")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfying(
                                equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                                equalTo(
                                    GEN_AI_OPERATION_NAME,
                                    GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues
                                        .CHAT),
                                equalTo(GEN_AI_REQUEST_MODEL, modelId),
                                equalTo(GEN_AI_USAGE_INPUT_TOKENS, 380),
                                equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 133),
                                equalTo(GEN_AI_RESPONSE_FINISH_REASONS, asList("tool_use")))));

    getTesting()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            metric ->
                metric
                    .hasName("gen_ai.client.token.usage")
                    .hasUnit("{token}")
                    .hasDescription("Measures number of input and output tokens used.")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(380)
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
                                        .hasSum(133)
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
                    .hasDescription("GenAI operation duration.")
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

    // Clear to allow asserting telemetry of user request and tool result processing separately.
    getTesting().clearData();

    messages.add(
        Document.mapBuilder()
            .putDocument("role", message.asMap().get("role"))
            .putDocument("content", message.asMap().get("content"))
            .build());
    messages.add(
        Document.mapBuilder()
            .putString("role", "user")
            .putList(
                "content",
                asList(
                    Document.mapBuilder()
                        .putString("type", "tool_result")
                        .putString("tool_use_id", seattleToolUseId)
                        .putString("content", "50 degrees and raining")
                        .build(),
                    Document.mapBuilder()
                        .putString("type", "tool_result")
                        .putString("tool_use_id", sanFranciscoToolUseId)
                        .putString("content", "70 degrees and sunny")
                        .build()))
            .build());

    Document requestPayload1 =
        Document.mapBuilder()
            .putString("anthropic_version", "bedrock-2023-05-31")
            .putNumber("max_tokens", 1000)
            .putList("messages", messages)
            .putDocument("tools", tools)
            .build();

    SdkJsonGenerator generator1 = new SdkJsonGenerator(new JsonFactory(), "application/json");
    DocumentTypeJsonMarshaller marshaller1 = new DocumentTypeJsonMarshaller(generator1);
    requestPayload1.accept(marshaller1);

    InvokeModelRequest request1 =
        InvokeModelRequest.builder()
            .modelId(modelId)
            .body(SdkBytes.fromByteArray(generator1.getBytes()))
            .build();

    InvokeModelResponse response1 = client.invokeModel(request1);

    JsonNode node1 = JsonNode.parser().parse(response1.body().asByteArray());
    Document message1 = node1.visit(new DocumentUnmarshaller());

    assertThat(message1.asMap().get("content").asList().get(0).asMap().get("text").asString())
        .contains("Seattle: 50 degrees and raining\n" + "San Francisco: 70 degrees and sunny");

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("chat anthropic.claude-3-5-sonnet-20240620-v1:0")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfying(
                                equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                                equalTo(
                                    GEN_AI_OPERATION_NAME,
                                    GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues
                                        .CHAT),
                                equalTo(GEN_AI_REQUEST_MODEL, modelId),
                                equalTo(GEN_AI_USAGE_INPUT_TOKENS, 590),
                                equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 132),
                                equalTo(GEN_AI_RESPONSE_FINISH_REASONS, asList("end_turn")))));

    getTesting()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            metric ->
                metric
                    .hasName("gen_ai.client.token.usage")
                    .hasUnit("{token}")
                    .hasDescription("Measures number of input and output tokens used.")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(590)
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
                                        .hasSum(132)
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
                    .hasDescription("GenAI operation duration.")
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
                    .hasBody(Value.of(Collections.emptyMap())),
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
  void testInvokeModelWithResponseStreamToolCallAnthropicClaudeNoMessageContent()
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

    String modelId = "anthropic.claude-3-5-sonnet-20240620-v1:0";
    Document tools =
        Document.listBuilder()
            .addDocument(
                Document.mapBuilder()
                    .putString("name", "get_current_weather")
                    .putString("description", "Get the current weather in a given location.")
                    .putDocument(
                        "input_schema",
                        Document.mapBuilder()
                            .putString("type", "object")
                            .putDocument(
                                "properties",
                                Document.mapBuilder()
                                    .putDocument(
                                        "location",
                                        Document.mapBuilder()
                                            .putString("type", "string")
                                            .putString("description", "The name of the city")
                                            .build())
                                    .build())
                            .putList("required", singletonList(Document.fromString("location")))
                            .build())
                    .build())
            .build();

    List<Document> messages = new ArrayList<>();
    messages.add(
        Document.mapBuilder()
            .putString("role", "user")
            .putList(
                "content",
                singletonList(
                    Document.mapBuilder()
                        .putString("type", "text")
                        .putString(
                            "text", "What is the weather in Seattle and San Francisco today?")
                        .build()))
            .build());

    Document requestPayload0 =
        Document.mapBuilder()
            .putString("anthropic_version", "bedrock-2023-05-31")
            .putNumber("max_tokens", 1000)
            .putList("messages", messages)
            .putDocument("tools", tools)
            .build();

    SdkJsonGenerator generator0 = new SdkJsonGenerator(new JsonFactory(), "application/json");
    DocumentTypeJsonMarshaller marshaller0 = new DocumentTypeJsonMarshaller(generator0);
    requestPayload0.accept(marshaller0);

    InvokeModelWithResponseStreamRequest request0 =
        InvokeModelWithResponseStreamRequest.builder()
            .modelId(modelId)
            .body(SdkBytes.fromByteArray(generator0.getBytes()))
            .build();

    StringBuilder text = new StringBuilder();
    List<ToolUseBlock> toolCalls = new ArrayList<>();

    InvokeModelWithResponseStreamResponseHandler responseHandler0 =
        InvokeModelWithResponseStreamResponseHandler.builder()
            .subscriber(
                InvokeModelWithResponseStreamResponseHandler.Visitor.builder()
                    .onChunk(
                        new Consumer<PayloadPart>() {
                          private ToolUseBlock.Builder currentToolUse;
                          private StringBuilder currentInputJson;

                          @Override
                          public void accept(PayloadPart chunk) {
                            JsonNode node = JsonNode.parser().parse(chunk.bytes().asByteArray());
                            DocumentUnmarshaller unmarshaller = new DocumentUnmarshaller();
                            Document result = node.visit(unmarshaller);

                            switch (result.asMap().get("type").asString()) {
                              case "content_block_start":
                                {
                                  Document toolUse = result.asMap().get("content_block");
                                  if (toolUse.asMap().get("type").asString().equals("tool_use")) {
                                    currentToolUse = ToolUseBlock.builder();
                                    currentInputJson = new StringBuilder();
                                    Document toolUseId = toolUse.asMap().get("id");
                                    if (toolUseId != null) {
                                      currentToolUse.toolUseId(toolUseId.asString());
                                    }
                                    Document name = toolUse.asMap().get("name");
                                    if (name != null) {
                                      currentToolUse.name(name.asString());
                                    }
                                  }
                                  break;
                                }
                              case "content_block_delta":
                                {
                                  Document delta = result.asMap().get("delta");
                                  switch (delta.asMap().get("type").asString()) {
                                    case "text_delta":
                                      {
                                        text.append(delta.asMap().get("text").asString());
                                        break;
                                      }
                                    case "input_json_delta":
                                      {
                                        Document input = delta.asMap().get("partial_json");
                                        currentInputJson.append(input.asString());
                                        break;
                                      }
                                    default:
                                      // fallthrough
                                  }
                                  break;
                                }
                              case "content_block_stop":
                                {
                                  if (currentToolUse != null) {
                                    JsonNode node1 =
                                        JsonNode.parser().parse(currentInputJson.toString());
                                    DocumentUnmarshaller unmarshaller1 = new DocumentUnmarshaller();
                                    Document result1 = node1.visit(unmarshaller1);
                                    currentToolUse.input(result1);
                                    toolCalls.add(currentToolUse.build());
                                    currentToolUse = null;
                                  }
                                  break;
                                }
                              default:
                                // fallthrough
                            }
                          }
                        })
                    .build())
            .build();

    client.invokeModelWithResponseStream(request0, responseHandler0).get();

    String seattleToolUseId0 = "";
    String sanFranciscoToolUseId0 = "";
    for (ToolUseBlock toolUse : toolCalls) {
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
                        span.hasName("chat anthropic.claude-3-5-sonnet-20240620-v1:0")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfying(
                                equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                                equalTo(
                                    GEN_AI_OPERATION_NAME,
                                    GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues
                                        .CHAT),
                                equalTo(GEN_AI_REQUEST_MODEL, modelId),
                                equalTo(GEN_AI_USAGE_INPUT_TOKENS, 380),
                                equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 144),
                                equalTo(GEN_AI_RESPONSE_FINISH_REASONS, asList("tool_use")))));

    getTesting()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            metric ->
                metric
                    .hasName("gen_ai.client.token.usage")
                    .hasUnit("{token}")
                    .hasDescription("Measures number of input and output tokens used.")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(380)
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
                                        .hasSum(144)
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
                    .hasDescription("GenAI operation duration.")
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

    // Clear to allow asserting telemetry of user request and tool result processing separately.
    getTesting().clearData();

    messages.add(
        Document.mapBuilder()
            .putString("role", "assistant")
            .putList(
                "content",
                asList(
                    Document.mapBuilder()
                        .putString("type", "text")
                        .putString("text", text.toString())
                        .build(),
                    Document.mapBuilder()
                        .putString("type", "tool_use")
                        .putString("id", toolCalls.get(0).toolUseId())
                        .putString("name", toolCalls.get(0).name())
                        .putDocument("input", toolCalls.get(0).input())
                        .build(),
                    Document.mapBuilder()
                        .putString("type", "tool_use")
                        .putString("id", toolCalls.get(1).toolUseId())
                        .putString("name", toolCalls.get(1).name())
                        .putDocument("input", toolCalls.get(1).input())
                        .build()))
            .build());
    messages.add(
        Document.mapBuilder()
            .putString("role", "user")
            .putList(
                "content",
                asList(
                    Document.mapBuilder()
                        .putString("type", "tool_result")
                        .putString("tool_use_id", seattleToolUseId)
                        .putString("content", "50 degrees and raining")
                        .build(),
                    Document.mapBuilder()
                        .putString("type", "tool_result")
                        .putString("tool_use_id", sanFranciscoToolUseId)
                        .putString("content", "70 degrees and sunny")
                        .build()))
            .build());

    Document requestPayload1 =
        Document.mapBuilder()
            .putString("anthropic_version", "bedrock-2023-05-31")
            .putNumber("max_tokens", 1000)
            .putList("messages", messages)
            .putDocument("tools", tools)
            .build();

    SdkJsonGenerator generator1 = new SdkJsonGenerator(new JsonFactory(), "application/json");
    DocumentTypeJsonMarshaller marshaller1 = new DocumentTypeJsonMarshaller(generator1);
    requestPayload1.accept(marshaller1);

    InvokeModelWithResponseStreamRequest request1 =
        InvokeModelWithResponseStreamRequest.builder()
            .modelId(modelId)
            .body(SdkBytes.fromByteArray(generator1.getBytes()))
            .build();

    text.setLength(0);
    InvokeModelWithResponseStreamResponseHandler responseHandler1 =
        InvokeModelWithResponseStreamResponseHandler.builder()
            .subscriber(
                InvokeModelWithResponseStreamResponseHandler.Visitor.builder()
                    .onChunk(
                        chunk -> {
                          JsonNode node = JsonNode.parser().parse(chunk.bytes().asByteArray());
                          DocumentUnmarshaller unmarshaller = new DocumentUnmarshaller();
                          Document result = node.visit(unmarshaller);

                          if (result.asMap().get("type").asString().equals("content_block_delta")) {
                            Document delta = result.asMap().get("delta");
                            Document t = delta.asMap().get("text");
                            if (t != null) {
                              text.append(t.asString());
                            }
                          }
                        })
                    .build())
            .build();

    client.invokeModelWithResponseStream(request1, responseHandler1).get();

    assertThat(text.toString())
        .contains(
            "Seattle: The current weather in Seattle is 50 degrees and raining.\n"
                + "\n"
                + "San Francisco: The current weather in San Francisco is 70 degrees and sunny.");

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("chat anthropic.claude-3-5-sonnet-20240620-v1:0")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfying(
                                equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                                equalTo(
                                    GEN_AI_OPERATION_NAME,
                                    GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues
                                        .CHAT),
                                equalTo(GEN_AI_REQUEST_MODEL, modelId),
                                equalTo(GEN_AI_USAGE_INPUT_TOKENS, 601),
                                equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 145),
                                equalTo(GEN_AI_RESPONSE_FINISH_REASONS, asList("end_turn")))));

    getTesting()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            metric ->
                metric
                    .hasName("gen_ai.client.token.usage")
                    .hasUnit("{token}")
                    .hasDescription("Measures number of input and output tokens used.")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(601)
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
                                        .hasSum(145)
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
                    .hasDescription("GenAI operation duration.")
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
                    .hasBody(Value.of(Collections.emptyMap())),
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
}
