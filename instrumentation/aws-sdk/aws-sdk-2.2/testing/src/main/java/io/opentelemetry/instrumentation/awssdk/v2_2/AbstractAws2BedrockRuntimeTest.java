/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MAX_TOKENS;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_STOP_SEQUENCES;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_TEMPERATURE;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_TOP_P;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_FINISH_REASONS;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_SYSTEM;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_TOKEN_TYPE;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_INPUT_TOKENS;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_OUTPUT_TOKENS;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiSystemIncubatingValues.AWS_BEDROCK;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.KeyValue;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.recording.RecordingExtension;
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.protocols.json.SdkJsonGenerator;
import software.amazon.awssdk.protocols.json.internal.marshall.DocumentTypeJsonMarshaller;
import software.amazon.awssdk.protocols.json.internal.unmarshall.document.DocumentUnmarshaller;
import software.amazon.awssdk.protocols.jsoncore.JsonNode;
import software.amazon.awssdk.regions.Region;
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
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
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

public abstract class AbstractAws2BedrockRuntimeTest {
  protected static final String INSTRUMENTATION_NAME = "io.opentelemetry.aws-sdk-2.2";

  private static final String API_URL = "https://bedrock-runtime.us-east-1.amazonaws.com";

  protected static final AttributeKey<String> EVENT_NAME = AttributeKey.stringKey("event.name");

  @RegisterExtension static final RecordingExtension recording = new RecordingExtension(API_URL);

  protected abstract InstrumentationExtension getTesting();

  protected abstract ClientOverrideConfiguration.Builder createOverrideConfigurationBuilder();

  protected abstract BedrockRuntimeAsyncClient configureBedrockRuntimeClient(
      BedrockRuntimeAsyncClient client);

  protected static void configureClient(AwsClientBuilder<?, ?> builder) {
    builder
        .region(Region.US_EAST_1)
        .endpointOverride(URI.create("http://localhost:" + recording.getPort()));
    if (recording.isRecording()) {
      builder.putAuthScheme(new FixedHostAwsV4AuthScheme(API_URL));
    } else {
      builder.credentialsProvider(
          StaticCredentialsProvider.create(AwsBasicCredentials.create("testing", "testing")));
    }
  }

  @Test
  void testConverseBasic() {
    BedrockRuntimeClientBuilder builder = BedrockRuntimeClient.builder();
    builder.overrideConfiguration(createOverrideConfigurationBuilder().build());
    configureClient(builder);
    BedrockRuntimeClient client = builder.build();

    String modelId = "amazon.titan-text-lite-v1";
    ConverseResponse response =
        client.converse(
            ConverseRequest.builder()
                .modelId(modelId)
                .messages(
                    Message.builder()
                        .role(ConversationRole.USER)
                        .content(ContentBlock.fromText("Say this is a test"))
                        .build())
                .build());

    assertThat(response.output().message().content().get(0).text())
        .isEqualTo("Hi there! How can I help you today?");

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("chat amazon.titan-text-lite-v1")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfying(
                                equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                                equalTo(
                                    GEN_AI_OPERATION_NAME,
                                    GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues
                                        .CHAT),
                                equalTo(GEN_AI_REQUEST_MODEL, modelId),
                                equalTo(GEN_AI_USAGE_INPUT_TOKENS, 8),
                                equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 14),
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
                                        .hasSum(8)
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
                                        .hasSum(14)
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

    SpanContext spanCtx = getTesting().waitForTraces(1).get(0).get(0).getSpanContext();

    getTesting()
        .waitAndAssertLogRecords(
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                        equalTo(EVENT_NAME, "gen_ai.user.message"))
                    .hasSpanContext(spanCtx)
                    .hasBody(Value.of(KeyValue.of("content", Value.of("Say this is a test")))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK), equalTo(EVENT_NAME, "gen_ai.choice"))
                    .hasSpanContext(spanCtx)
                    .hasBody(
                        Value.of(
                            KeyValue.of("finish_reason", Value.of("end_turn")),
                            KeyValue.of("index", Value.of(0)),
                            KeyValue.of(
                                "content", Value.of("Hi there! How can I help you today?")))));
  }

  @Test
  void testConverseOptions() {
    BedrockRuntimeClientBuilder builder = BedrockRuntimeClient.builder();
    builder.overrideConfiguration(createOverrideConfigurationBuilder().build());
    configureClient(builder);
    BedrockRuntimeClient client = builder.build();

    String modelId = "amazon.titan-text-lite-v1";
    ConverseResponse response =
        client.converse(
            ConverseRequest.builder()
                .modelId(modelId)
                .messages(
                    Message.builder()
                        .role(ConversationRole.USER)
                        .content(ContentBlock.fromText("Say this is a test"))
                        .build())
                .inferenceConfig(
                    InferenceConfiguration.builder()
                        .maxTokens(10)
                        .temperature(0.8f)
                        .topP(1f)
                        .stopSequences("|")
                        .build())
                .build());

    assertThat(response.output().message().content().get(0).text()).isEqualTo("This is an LLM (");

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("chat amazon.titan-text-lite-v1")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfying(
                                equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                                equalTo(
                                    GEN_AI_OPERATION_NAME,
                                    GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues
                                        .CHAT),
                                equalTo(GEN_AI_REQUEST_MODEL, modelId),
                                equalTo(GEN_AI_REQUEST_MAX_TOKENS, 10),
                                satisfies(
                                    GEN_AI_REQUEST_TEMPERATURE,
                                    temp -> temp.isCloseTo(0.8, within(0.0001))),
                                equalTo(GEN_AI_REQUEST_TOP_P, 1.0),
                                equalTo(GEN_AI_REQUEST_STOP_SEQUENCES, asList("|")),
                                equalTo(GEN_AI_USAGE_INPUT_TOKENS, 8),
                                equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 10),
                                equalTo(GEN_AI_RESPONSE_FINISH_REASONS, asList("max_tokens")))));

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
                                        .hasSum(8)
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
                                        .hasSum(10)
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

    SpanContext spanCtx = getTesting().waitForTraces(1).get(0).get(0).getSpanContext();

    getTesting()
        .waitAndAssertLogRecords(
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                        equalTo(EVENT_NAME, "gen_ai.user.message"))
                    .hasSpanContext(spanCtx)
                    .hasBody(Value.of(KeyValue.of("content", Value.of("Say this is a test")))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK), equalTo(EVENT_NAME, "gen_ai.choice"))
                    .hasSpanContext(spanCtx)
                    .hasBody(
                        Value.of(
                            KeyValue.of("finish_reason", Value.of("max_tokens")),
                            KeyValue.of("index", Value.of(0)),
                            KeyValue.of("content", Value.of("This is an LLM (")))));
  }

  @Test
  void testConverseToolCall() {
    BedrockRuntimeClientBuilder builder = BedrockRuntimeClient.builder();
    builder.overrideConfiguration(createOverrideConfigurationBuilder().build());
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
                    .hasBody(
                        Value.of(
                            KeyValue.of(
                                "content",
                                Value.of(
                                    "What is the weather in Seattle and San Francisco today?")))),
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
                                        KeyValue.of(
                                            "arguments", Value.of("{\"location\":\"Seattle\"}")),
                                        KeyValue.of("id", Value.of(seattleToolUseId)),
                                        KeyValue.of("type", Value.of("function"))),
                                    Value.of(
                                        KeyValue.of("name", Value.of("get_current_weather")),
                                        KeyValue.of(
                                            "arguments",
                                            Value.of("{\"location\":\"San Francisco\"}")),
                                        KeyValue.of("id", Value.of(sanFranciscoToolUseId)),
                                        KeyValue.of("type", Value.of("function"))))),
                            KeyValue.of(
                                "content",
                                Value.of(
                                    "<thinking> The User has asked for the current weather in two locations: Seattle and San Francisco. To provide the requested information, I will use the \"get_current_weather\" tool for each location separately. </thinking>\n")))));

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
                    .hasBody(
                        Value.of(
                            KeyValue.of(
                                "content",
                                Value.of(
                                    "What is the weather in Seattle and San Francisco today?")))),
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
                                        KeyValue.of(
                                            "arguments", Value.of("{\"location\":\"Seattle\"}")),
                                        KeyValue.of("id", Value.of(seattleToolUseId)),
                                        KeyValue.of("type", Value.of("function"))),
                                    Value.of(
                                        KeyValue.of("name", Value.of("get_current_weather")),
                                        KeyValue.of(
                                            "arguments",
                                            Value.of("{\"location\":\"San Francisco\"}")),
                                        KeyValue.of("id", Value.of(sanFranciscoToolUseId)),
                                        KeyValue.of("type", Value.of("function"))))),
                            KeyValue.of(
                                "content",
                                Value.of(
                                    "<thinking> The User has asked for the current weather in two locations: Seattle and San Francisco. To provide the requested information, I will use the \"get_current_weather\" tool for each location separately. </thinking>\n")))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                        equalTo(EVENT_NAME, "gen_ai.tool.message"))
                    .hasSpanContext(spanCtx1)
                    .hasBody(
                        Value.of(
                            KeyValue.of("id", Value.of(seattleToolUseId)),
                            KeyValue.of(
                                "content", Value.of("{\"weather\":\"50 degrees and raining\"}")))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                        equalTo(EVENT_NAME, "gen_ai.tool.message"))
                    .hasSpanContext(spanCtx1)
                    .hasBody(
                        Value.of(
                            KeyValue.of("id", Value.of(sanFranciscoToolUseId)),
                            KeyValue.of(
                                "content", Value.of("{\"weather\":\"70 degrees and sunny\"}")))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK), equalTo(EVENT_NAME, "gen_ai.choice"))
                    .hasSpanContext(spanCtx1)
                    .hasBody(
                        Value.of(
                            KeyValue.of("finish_reason", Value.of("end_turn")),
                            KeyValue.of("index", Value.of(0)),
                            KeyValue.of(
                                "content",
                                Value.of(
                                    "<thinking> The tool has provided the current weather for both locations. Now I will compile this information and present it to the User. </thinking>\n"
                                        + "\n"
                                        + "The current weather in Seattle is 50 degrees and raining. In San Francisco, the weather is 70 degrees and sunny.")))));
  }

  @Test
  void testConverseToolCallStream() throws InterruptedException, ExecutionException {
    BedrockRuntimeAsyncClientBuilder builder = BedrockRuntimeAsyncClient.builder();
    builder.overrideConfiguration(createOverrideConfigurationBuilder().build());
    configureClient(builder);
    BedrockRuntimeAsyncClient client = configureBedrockRuntimeClient(builder.build());

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
                    .hasBody(
                        Value.of(
                            KeyValue.of(
                                "content",
                                Value.of(
                                    "What is the weather in Seattle and San Francisco today?")))),
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
                                        KeyValue.of(
                                            "arguments", Value.of("{\"location\":\"Seattle\"}")),
                                        KeyValue.of("id", Value.of(seattleToolUseId)),
                                        KeyValue.of("type", Value.of("function"))),
                                    Value.of(
                                        KeyValue.of("name", Value.of("get_current_weather")),
                                        KeyValue.of(
                                            "arguments",
                                            Value.of("{\"location\":\"San Francisco\"}")),
                                        KeyValue.of("id", Value.of(sanFranciscoToolUseId)),
                                        KeyValue.of("type", Value.of("function"))))),
                            KeyValue.of(
                                "content",
                                Value.of(
                                    "<thinking> The User has asked for the current weather in two locations: Seattle and San Francisco. To provide the requested information, I will use the \"get_current_weather\" tool for each location separately. </thinking>\n")))));

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
                    .hasBody(
                        Value.of(
                            KeyValue.of(
                                "content",
                                Value.of(
                                    "What is the weather in Seattle and San Francisco today?")))),
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
                                        KeyValue.of(
                                            "arguments", Value.of("{\"location\":\"Seattle\"}")),
                                        KeyValue.of("id", Value.of(seattleToolUseId)),
                                        KeyValue.of("type", Value.of("function"))),
                                    Value.of(
                                        KeyValue.of("name", Value.of("get_current_weather")),
                                        KeyValue.of(
                                            "arguments",
                                            Value.of("{\"location\":\"San Francisco\"}")),
                                        KeyValue.of("id", Value.of(sanFranciscoToolUseId)),
                                        KeyValue.of("type", Value.of("function"))))),
                            KeyValue.of(
                                "content",
                                Value.of(
                                    "<thinking> The User has asked for the current weather in two locations: Seattle and San Francisco. To provide the requested information, I will use the \"get_current_weather\" tool for each location separately. </thinking>\n")))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                        equalTo(EVENT_NAME, "gen_ai.tool.message"))
                    .hasSpanContext(spanCtx1)
                    .hasBody(
                        Value.of(
                            KeyValue.of("id", Value.of(seattleToolUseId)),
                            KeyValue.of(
                                "content", Value.of("{\"weather\":\"50 degrees and raining\"}")))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                        equalTo(EVENT_NAME, "gen_ai.tool.message"))
                    .hasSpanContext(spanCtx1)
                    .hasBody(
                        Value.of(
                            KeyValue.of("id", Value.of(sanFranciscoToolUseId)),
                            KeyValue.of(
                                "content", Value.of("{\"weather\":\"70 degrees and sunny\"}")))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK), equalTo(EVENT_NAME, "gen_ai.choice"))
                    .hasSpanContext(spanCtx1)
                    .hasBody(
                        Value.of(
                            KeyValue.of("finish_reason", Value.of("end_turn")),
                            KeyValue.of("index", Value.of(0)),
                            KeyValue.of(
                                "content",
                                Value.of(
                                    "<thinking> The tool has provided the current weather for both locations. Now I will compile the information and present it to the User. </thinking>\n"
                                        + "\n"
                                        + "The current weather in Seattle is 50 degrees and it is raining. In San Francisco, the weather is 70 degrees and sunny.")))));
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
  void testConverseStream() throws InterruptedException, ExecutionException {
    BedrockRuntimeAsyncClientBuilder builder = BedrockRuntimeAsyncClient.builder();
    builder.overrideConfiguration(createOverrideConfigurationBuilder().build());
    configureClient(builder);
    BedrockRuntimeAsyncClient client = configureBedrockRuntimeClient(builder.build());

    String modelId = "amazon.titan-text-lite-v1";

    List<String> responseChunks = new ArrayList<>();

    ConverseStreamResponseHandler responseHandler =
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
                .messages(
                    Message.builder()
                        .role(ConversationRole.USER)
                        .content(ContentBlock.fromText("Say this is a test"))
                        .build())
                .build(),
            responseHandler)
        .get();

    assertThat(String.join("", responseChunks)).isEqualTo("\"Test, test\"");

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("chat amazon.titan-text-lite-v1")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfying(
                                equalTo(
                                    GEN_AI_SYSTEM,
                                    GenAiIncubatingAttributes.GenAiSystemIncubatingValues
                                        .AWS_BEDROCK),
                                equalTo(
                                    GEN_AI_OPERATION_NAME,
                                    GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues
                                        .CHAT),
                                equalTo(GEN_AI_REQUEST_MODEL, modelId),
                                equalTo(GEN_AI_USAGE_INPUT_TOKENS, 8),
                                equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 10),
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
                                        .hasSum(8)
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
                                        .hasSum(10)
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

    SpanContext spanCtx = getTesting().waitForTraces(1).get(0).get(0).getSpanContext();

    getTesting()
        .waitAndAssertLogRecords(
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                        equalTo(EVENT_NAME, "gen_ai.user.message"))
                    .hasSpanContext(spanCtx)
                    .hasBody(Value.of(KeyValue.of("content", Value.of("Say this is a test")))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK), equalTo(EVENT_NAME, "gen_ai.choice"))
                    .hasSpanContext(spanCtx)
                    .hasBody(
                        Value.of(
                            KeyValue.of("finish_reason", Value.of("end_turn")),
                            KeyValue.of("index", Value.of(0)),
                            KeyValue.of("content", Value.of("\"Test, test\"")))));
  }

  @Test
  void testConverseStreamOptions() throws InterruptedException, ExecutionException {
    BedrockRuntimeAsyncClientBuilder builder = BedrockRuntimeAsyncClient.builder();
    builder.overrideConfiguration(createOverrideConfigurationBuilder().build());
    configureClient(builder);
    BedrockRuntimeAsyncClient client = configureBedrockRuntimeClient(builder.build());

    String modelId = "amazon.titan-text-lite-v1";

    List<String> responseChunks = new ArrayList<>();

    ConverseStreamResponseHandler responseHandler =
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
                .messages(
                    Message.builder()
                        .role(ConversationRole.USER)
                        .content(ContentBlock.fromText("Say this is a test"))
                        .build())
                .inferenceConfig(
                    InferenceConfiguration.builder()
                        .maxTokens(5)
                        .temperature(0.8f)
                        .topP(1f)
                        .stopSequences("|")
                        .build())
                .build(),
            responseHandler)
        .get();

    assertThat(String.join("", responseChunks)).isEqualTo("This model");

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("chat amazon.titan-text-lite-v1")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfying(
                                equalTo(
                                    GEN_AI_SYSTEM,
                                    GenAiIncubatingAttributes.GenAiSystemIncubatingValues
                                        .AWS_BEDROCK),
                                equalTo(
                                    GEN_AI_OPERATION_NAME,
                                    GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues
                                        .CHAT),
                                equalTo(GEN_AI_REQUEST_MODEL, modelId),
                                equalTo(GEN_AI_REQUEST_MAX_TOKENS, 5),
                                satisfies(
                                    GEN_AI_REQUEST_TEMPERATURE,
                                    temp -> temp.isCloseTo(0.8, within(0.0001))),
                                equalTo(GEN_AI_REQUEST_TOP_P, 1.0),
                                equalTo(GEN_AI_REQUEST_STOP_SEQUENCES, asList("|")),
                                equalTo(GEN_AI_USAGE_INPUT_TOKENS, 8),
                                equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 5),
                                equalTo(GEN_AI_RESPONSE_FINISH_REASONS, asList("max_tokens")))));

    SpanContext spanCtx = getTesting().waitForTraces(1).get(0).get(0).getSpanContext();

    getTesting()
        .waitAndAssertLogRecords(
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                        equalTo(EVENT_NAME, "gen_ai.user.message"))
                    .hasSpanContext(spanCtx)
                    .hasBody(Value.of(KeyValue.of("content", Value.of("Say this is a test")))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK), equalTo(EVENT_NAME, "gen_ai.choice"))
                    .hasSpanContext(spanCtx)
                    .hasBody(
                        Value.of(
                            KeyValue.of("finish_reason", Value.of("max_tokens")),
                            KeyValue.of("index", Value.of(0)),
                            KeyValue.of("content", Value.of("This model")))));
  }

  @Test
  void testInvokeModelAmazonTitan() {
    BedrockRuntimeClientBuilder builder = BedrockRuntimeClient.builder();
    builder.overrideConfiguration(createOverrideConfigurationBuilder().build());
    configureClient(builder);
    BedrockRuntimeClient client = builder.build();

    String modelId = "amazon.titan-text-lite-v1";

    Document requestPayload =
        Document.mapBuilder()
            .putString("inputText", "Say this is a test")
            .putDocument(
                "textGenerationConfig",
                Document.mapBuilder()
                    .putNumber("maxTokenCount", 10)
                    .putNumber("temperature", 0.8f)
                    .putNumber("topP", 1)
                    .putList("stopSequences", singletonList(Document.fromString("|")))
                    .build())
            .build();

    SdkJsonGenerator generator = new SdkJsonGenerator(new JsonFactory(), "application/json");
    DocumentTypeJsonMarshaller marshaller = new DocumentTypeJsonMarshaller(generator);
    requestPayload.accept(marshaller);

    InvokeModelRequest request =
        InvokeModelRequest.builder()
            .modelId(modelId)
            .body(SdkBytes.fromByteArray(generator.getBytes()))
            .build();

    InvokeModelResponse response = client.invokeModel(request);

    JsonNode node = JsonNode.parser().parse(response.body().asByteArray());
    Document responsePayload = node.visit(new DocumentUnmarshaller());
    Document result = responsePayload.asMap().get("results").asList().get(0);
    assertThat(result.asMap().get("outputText").asString())
        .isEqualTo("\nHello! I am a computer program designed to");

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("text_completion amazon.titan-text-lite-v1")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfying(
                                equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                                equalTo(
                                    GEN_AI_OPERATION_NAME,
                                    GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues
                                        .TEXT_COMPLETION),
                                equalTo(GEN_AI_REQUEST_MODEL, modelId),
                                equalTo(GEN_AI_REQUEST_MAX_TOKENS, 10),
                                satisfies(
                                    GEN_AI_REQUEST_TEMPERATURE,
                                    temp -> temp.isCloseTo(0.8, within(0.0001))),
                                equalTo(GEN_AI_REQUEST_TOP_P, 1.0),
                                equalTo(GEN_AI_REQUEST_STOP_SEQUENCES, asList("|")),
                                equalTo(GEN_AI_USAGE_INPUT_TOKENS, 5),
                                equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 10),
                                equalTo(GEN_AI_RESPONSE_FINISH_REASONS, asList("LENGTH")))));

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
                                        .hasSum(5)
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
                                                    .GenAiOperationNameIncubatingValues
                                                    .TEXT_COMPLETION),
                                            equalTo(GEN_AI_REQUEST_MODEL, modelId)),
                                point ->
                                    point
                                        .hasSum(10)
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
                                                    .GenAiOperationNameIncubatingValues
                                                    .TEXT_COMPLETION),
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
                                                    .GenAiOperationNameIncubatingValues
                                                    .TEXT_COMPLETION),
                                            equalTo(GEN_AI_REQUEST_MODEL, modelId)))));

    SpanContext spanCtx = getTesting().waitForTraces(1).get(0).get(0).getSpanContext();

    getTesting()
        .waitAndAssertLogRecords(
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                        equalTo(EVENT_NAME, "gen_ai.user.message"))
                    .hasSpanContext(spanCtx)
                    .hasBody(Value.of(KeyValue.of("content", Value.of("Say this is a test")))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK), equalTo(EVENT_NAME, "gen_ai.choice"))
                    .hasSpanContext(spanCtx)
                    .hasBody(
                        Value.of(
                            KeyValue.of("finish_reason", Value.of("LENGTH")),
                            KeyValue.of("index", Value.of(0)),
                            KeyValue.of(
                                "content",
                                Value.of("\nHello! I am a computer program designed to")))));
  }

  @Test
  void testInvokeModelWithResponseStreamAmazonTitan()
      throws InterruptedException, ExecutionException {
    BedrockRuntimeAsyncClientBuilder builder = BedrockRuntimeAsyncClient.builder();
    builder.overrideConfiguration(createOverrideConfigurationBuilder().build());
    configureClient(builder);
    BedrockRuntimeAsyncClient client = configureBedrockRuntimeClient(builder.build());

    String modelId = "amazon.titan-text-lite-v1";

    Document requestPayload =
        Document.mapBuilder()
            // Long output string to trigger multiple chunks.
            .putString("inputText", "List out every country in the world")
            .putDocument(
                "textGenerationConfig",
                Document.mapBuilder()
                    .putNumber("maxTokenCount", 100)
                    .putNumber("temperature", 0.8f)
                    .putNumber("topP", 1)
                    .putList("stopSequences", singletonList(Document.fromString("|")))
                    .build())
            .build();

    SdkJsonGenerator generator = new SdkJsonGenerator(new JsonFactory(), "application/json");
    DocumentTypeJsonMarshaller marshaller = new DocumentTypeJsonMarshaller(generator);
    requestPayload.accept(marshaller);

    InvokeModelWithResponseStreamRequest request =
        InvokeModelWithResponseStreamRequest.builder()
            .modelId(modelId)
            .body(SdkBytes.fromByteArray(generator.getBytes()))
            .build();

    StringBuilder text = new StringBuilder();

    InvokeModelWithResponseStreamResponseHandler responseHandler =
        InvokeModelWithResponseStreamResponseHandler.builder()
            .subscriber(
                InvokeModelWithResponseStreamResponseHandler.Visitor.builder()
                    .onChunk(
                        chunk -> {
                          JsonNode node = JsonNode.parser().parse(chunk.bytes().asByteArray());
                          DocumentUnmarshaller unmarshaller = new DocumentUnmarshaller();
                          Document result = node.visit(unmarshaller);
                          text.append(result.asMap().get("outputText").asString());
                        })
                    .build())
            .build();

    client.invokeModelWithResponseStream(request, responseHandler).get();

    assertThat(text.toString()).contains("Here is the list of every country in the world");

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("text_completion amazon.titan-text-lite-v1")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfying(
                                equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                                equalTo(
                                    GEN_AI_OPERATION_NAME,
                                    GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues
                                        .TEXT_COMPLETION),
                                equalTo(GEN_AI_REQUEST_MODEL, modelId),
                                equalTo(GEN_AI_REQUEST_MAX_TOKENS, 100),
                                satisfies(
                                    GEN_AI_REQUEST_TEMPERATURE,
                                    temp -> temp.isCloseTo(0.8, within(0.0001))),
                                equalTo(GEN_AI_REQUEST_TOP_P, 1.0),
                                equalTo(GEN_AI_REQUEST_STOP_SEQUENCES, asList("|")),
                                equalTo(GEN_AI_USAGE_INPUT_TOKENS, 7),
                                equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 100),
                                equalTo(GEN_AI_RESPONSE_FINISH_REASONS, asList("LENGTH")))));

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
                                        .hasSum(7)
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
                                                    .GenAiOperationNameIncubatingValues
                                                    .TEXT_COMPLETION),
                                            equalTo(GEN_AI_REQUEST_MODEL, modelId)),
                                point ->
                                    point
                                        .hasSum(100)
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
                                                    .GenAiOperationNameIncubatingValues
                                                    .TEXT_COMPLETION),
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
                                                    .GenAiOperationNameIncubatingValues
                                                    .TEXT_COMPLETION),
                                            equalTo(GEN_AI_REQUEST_MODEL, modelId)))));

    SpanContext spanCtx = getTesting().waitForTraces(1).get(0).get(0).getSpanContext();

    getTesting()
        .waitAndAssertLogRecords(
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                        equalTo(EVENT_NAME, "gen_ai.user.message"))
                    .hasSpanContext(spanCtx)
                    .hasBody(
                        Value.of(
                            KeyValue.of(
                                "content", Value.of("List out every country in the world")))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK), equalTo(EVENT_NAME, "gen_ai.choice"))
                    .hasSpanContext(spanCtx)
                    .hasBody(
                        Value.of(
                            KeyValue.of("finish_reason", Value.of("LENGTH")),
                            KeyValue.of("index", Value.of(0)),
                            KeyValue.of("content", Value.of(text.toString())))));
  }

  @Test
  void testInvokeModelAmazonNova() {
    BedrockRuntimeClientBuilder builder = BedrockRuntimeClient.builder();
    builder.overrideConfiguration(createOverrideConfigurationBuilder().build());
    configureClient(builder);
    BedrockRuntimeClient client = builder.build();

    String modelId = "amazon.nova-micro-v1:0";

    Document requestPayload =
        Document.mapBuilder()
            .putList(
                "messages",
                singletonList(
                    Document.mapBuilder()
                        .putString("role", "user")
                        .putList(
                            "content",
                            singletonList(
                                Document.mapBuilder()
                                    .putString("text", "Say this is a test")
                                    .build()))
                        .build()))
            .putDocument(
                "inferenceConfig",
                Document.mapBuilder()
                    .putNumber("max_new_tokens", 10)
                    .putNumber("temperature", 0.8f)
                    .putNumber("topP", 1)
                    .putList("stopSequences", singletonList(Document.fromString("|")))
                    .build())
            .build();

    SdkJsonGenerator generator = new SdkJsonGenerator(new JsonFactory(), "application/json");
    DocumentTypeJsonMarshaller marshaller = new DocumentTypeJsonMarshaller(generator);
    requestPayload.accept(marshaller);

    InvokeModelRequest request =
        InvokeModelRequest.builder()
            .modelId(modelId)
            .body(SdkBytes.fromByteArray(generator.getBytes()))
            .build();

    InvokeModelResponse response = client.invokeModel(request);

    JsonNode node = JsonNode.parser().parse(response.body().asByteArray());
    Document responsePayload = node.visit(new DocumentUnmarshaller());
    assertThat(
            responsePayload
                .asMap()
                .get("output")
                .asMap()
                .get("message")
                .asMap()
                .get("content")
                .asList()
                .get(0)
                .asMap()
                .get("text")
                .asString())
        .isEqualTo("It sounds like you're initiating a test or");

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
                                equalTo(GEN_AI_REQUEST_MAX_TOKENS, 10),
                                satisfies(
                                    GEN_AI_REQUEST_TEMPERATURE,
                                    temp -> temp.isCloseTo(0.8, within(0.0001))),
                                equalTo(GEN_AI_REQUEST_TOP_P, 1.0),
                                equalTo(GEN_AI_REQUEST_STOP_SEQUENCES, asList("|")),
                                equalTo(GEN_AI_USAGE_INPUT_TOKENS, 5),
                                equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 10),
                                equalTo(GEN_AI_RESPONSE_FINISH_REASONS, asList("max_tokens")))));

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
                                        .hasSum(5)
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
                                        .hasSum(10)
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

    SpanContext spanCtx = getTesting().waitForTraces(1).get(0).get(0).getSpanContext();

    getTesting()
        .waitAndAssertLogRecords(
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                        equalTo(EVENT_NAME, "gen_ai.user.message"))
                    .hasSpanContext(spanCtx)
                    .hasBody(Value.of(KeyValue.of("content", Value.of("Say this is a test")))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK), equalTo(EVENT_NAME, "gen_ai.choice"))
                    .hasSpanContext(spanCtx)
                    .hasBody(
                        Value.of(
                            KeyValue.of("finish_reason", Value.of("max_tokens")),
                            KeyValue.of("index", Value.of(0)),
                            KeyValue.of(
                                "content",
                                Value.of("It sounds like you're initiating a test or")))));
  }

  @Test
  void testInvokeModelWithResponseStreamAmazonNova()
      throws InterruptedException, ExecutionException {
    BedrockRuntimeAsyncClientBuilder builder = BedrockRuntimeAsyncClient.builder();
    builder.overrideConfiguration(createOverrideConfigurationBuilder().build());
    configureClient(builder);
    BedrockRuntimeAsyncClient client = configureBedrockRuntimeClient(builder.build());

    String modelId = "amazon.nova-micro-v1:0";

    Document requestPayload =
        Document.mapBuilder()
            .putList(
                "messages",
                singletonList(
                    Document.mapBuilder()
                        .putString("role", "user")
                        .putList(
                            "content",
                            singletonList(
                                Document.mapBuilder()
                                    // Long output string to trigger multiple chunks.
                                    .putString("text", "List out every country in the world")
                                    .build()))
                        .build()))
            .putDocument(
                "inferenceConfig",
                Document.mapBuilder()
                    .putNumber("max_new_tokens", 100)
                    .putNumber("temperature", 0.8f)
                    .putNumber("topP", 1)
                    .putList("stopSequences", singletonList(Document.fromString("|")))
                    .build())
            .build();

    SdkJsonGenerator generator = new SdkJsonGenerator(new JsonFactory(), "application/json");
    DocumentTypeJsonMarshaller marshaller = new DocumentTypeJsonMarshaller(generator);
    requestPayload.accept(marshaller);

    InvokeModelWithResponseStreamRequest request =
        InvokeModelWithResponseStreamRequest.builder()
            .modelId(modelId)
            .body(SdkBytes.fromByteArray(generator.getBytes()))
            .build();

    StringBuilder text = new StringBuilder();

    InvokeModelWithResponseStreamResponseHandler responseHandler =
        InvokeModelWithResponseStreamResponseHandler.builder()
            .subscriber(
                InvokeModelWithResponseStreamResponseHandler.Visitor.builder()
                    .onChunk(
                        chunk -> {
                          JsonNode node = JsonNode.parser().parse(chunk.bytes().asByteArray());
                          DocumentUnmarshaller unmarshaller = new DocumentUnmarshaller();
                          Document result = node.visit(unmarshaller);
                          Document block = result.asMap().get("contentBlockDelta");
                          if (block == null) {
                            return;
                          }
                          Document delta = block.asMap().get("delta");
                          if (delta == null) {
                            return;
                          }
                          text.append(delta.asMap().get("text").asString());
                        })
                    .build())
            .build();

    client.invokeModelWithResponseStream(request, responseHandler).get();

    assertThat(text.toString())
        .contains("Listing every country in the world is a comprehensive task");

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
                                equalTo(GEN_AI_REQUEST_MAX_TOKENS, 100),
                                satisfies(
                                    GEN_AI_REQUEST_TEMPERATURE,
                                    temp -> temp.isCloseTo(0.8, within(0.0001))),
                                equalTo(GEN_AI_REQUEST_TOP_P, 1.0),
                                equalTo(GEN_AI_REQUEST_STOP_SEQUENCES, asList("|")),
                                equalTo(GEN_AI_USAGE_INPUT_TOKENS, 7),
                                equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 100),
                                equalTo(GEN_AI_RESPONSE_FINISH_REASONS, asList("max_tokens")))));

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
                                        .hasSum(7)
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
                                        .hasSum(100)
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

    SpanContext spanCtx = getTesting().waitForTraces(1).get(0).get(0).getSpanContext();

    getTesting()
        .waitAndAssertLogRecords(
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                        equalTo(EVENT_NAME, "gen_ai.user.message"))
                    .hasSpanContext(spanCtx)
                    .hasBody(
                        Value.of(
                            KeyValue.of(
                                "content", Value.of("List out every country in the world")))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK), equalTo(EVENT_NAME, "gen_ai.choice"))
                    .hasSpanContext(spanCtx)
                    .hasBody(
                        Value.of(
                            KeyValue.of("finish_reason", Value.of("max_tokens")),
                            KeyValue.of("index", Value.of(0)),
                            KeyValue.of("content", Value.of(text.toString())))));
  }

  @Test
  void testInvokeModelAnthropicClaude() {
    BedrockRuntimeClientBuilder builder = BedrockRuntimeClient.builder();
    builder.overrideConfiguration(createOverrideConfigurationBuilder().build());
    configureClient(builder);
    BedrockRuntimeClient client = builder.build();

    String modelId = "anthropic.claude-v2";

    Document requestPayload =
        Document.mapBuilder()
            .putList(
                "messages",
                singletonList(
                    Document.mapBuilder()
                        .putString("role", "user")
                        .putList(
                            "content",
                            singletonList(
                                Document.mapBuilder()
                                    .putString("text", "Say this is a test")
                                    .putString("type", "text")
                                    .build()))
                        .build()))
            .putString("anthropic_version", "bedrock-2023-05-31")
            .putNumber("max_tokens", 10)
            .putNumber("temperature", 0.8f)
            .putNumber("top_p", 1)
            .putList("stop_sequences", singletonList(Document.fromString("|")))
            .build();

    SdkJsonGenerator generator = new SdkJsonGenerator(new JsonFactory(), "application/json");
    DocumentTypeJsonMarshaller marshaller = new DocumentTypeJsonMarshaller(generator);
    requestPayload.accept(marshaller);

    InvokeModelRequest request =
        InvokeModelRequest.builder()
            .modelId(modelId)
            .body(SdkBytes.fromByteArray(generator.getBytes()))
            .build();

    InvokeModelResponse response = client.invokeModel(request);

    JsonNode node = JsonNode.parser().parse(response.body().asByteArray());
    Document responsePayload = node.visit(new DocumentUnmarshaller());
    assertThat(
            responsePayload.asMap().get("content").asList().get(0).asMap().get("text").asString())
        .isEqualTo("Okay, I just said \"This is a test");

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("chat anthropic.claude-v2")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfying(
                                equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                                equalTo(
                                    GEN_AI_OPERATION_NAME,
                                    GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues
                                        .CHAT),
                                equalTo(GEN_AI_REQUEST_MODEL, modelId),
                                equalTo(GEN_AI_REQUEST_MAX_TOKENS, 10),
                                satisfies(
                                    GEN_AI_REQUEST_TEMPERATURE,
                                    temp -> temp.isCloseTo(0.8, within(0.0001))),
                                equalTo(GEN_AI_REQUEST_TOP_P, 1.0),
                                equalTo(GEN_AI_REQUEST_STOP_SEQUENCES, asList("|")),
                                equalTo(GEN_AI_USAGE_INPUT_TOKENS, 14),
                                equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 10),
                                equalTo(GEN_AI_RESPONSE_FINISH_REASONS, asList("max_tokens")))));

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
                                        .hasSum(14)
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
                                        .hasSum(10)
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

    SpanContext spanCtx = getTesting().waitForTraces(1).get(0).get(0).getSpanContext();

    getTesting()
        .waitAndAssertLogRecords(
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                        equalTo(EVENT_NAME, "gen_ai.user.message"))
                    .hasSpanContext(spanCtx)
                    .hasBody(Value.of(KeyValue.of("content", Value.of("Say this is a test")))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK), equalTo(EVENT_NAME, "gen_ai.choice"))
                    .hasSpanContext(spanCtx)
                    .hasBody(
                        Value.of(
                            KeyValue.of("finish_reason", Value.of("max_tokens")),
                            KeyValue.of("index", Value.of(0)),
                            KeyValue.of(
                                "content", Value.of("Okay, I just said \"This is a test")))));
  }

  @Test
  void testInvokeModelMetaLlama() {
    BedrockRuntimeClientBuilder builder = BedrockRuntimeClient.builder();
    builder.overrideConfiguration(createOverrideConfigurationBuilder().build());
    configureClient(builder);
    BedrockRuntimeClient client = builder.build();

    String modelId = "meta.llama3-3-70b-instruct-v1:0";

    Document requestPayload =
        Document.mapBuilder()
            .putString("prompt", "Say this is a test")
            .putNumber("max_gen_len", 10)
            .putNumber("temperature", 0.8f)
            .putNumber("top_p", 1)
            .build();

    SdkJsonGenerator generator = new SdkJsonGenerator(new JsonFactory(), "application/json");
    DocumentTypeJsonMarshaller marshaller = new DocumentTypeJsonMarshaller(generator);
    requestPayload.accept(marshaller);

    InvokeModelRequest request =
        InvokeModelRequest.builder()
            .modelId(modelId)
            .body(SdkBytes.fromByteArray(generator.getBytes()))
            .build();

    InvokeModelResponse response = client.invokeModel(request);

    JsonNode node = JsonNode.parser().parse(response.body().asByteArray());
    Document responsePayload = node.visit(new DocumentUnmarshaller());
    assertThat(responsePayload.asMap().get("generation").asString())
        .isEqualTo("\n\nThis is a test");

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("chat meta.llama3-3-70b-instruct-v1:0")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfying(
                                equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                                equalTo(
                                    GEN_AI_OPERATION_NAME,
                                    GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues
                                        .CHAT),
                                equalTo(GEN_AI_REQUEST_MODEL, modelId),
                                equalTo(GEN_AI_REQUEST_MAX_TOKENS, 10),
                                satisfies(
                                    GEN_AI_REQUEST_TEMPERATURE,
                                    temp -> temp.isCloseTo(0.8, within(0.0001))),
                                equalTo(GEN_AI_REQUEST_TOP_P, 1.0),
                                equalTo(GEN_AI_USAGE_INPUT_TOKENS, 5),
                                equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 10),
                                equalTo(GEN_AI_RESPONSE_FINISH_REASONS, asList("max_tokens")))));
  }

  @Test
  void testInvokeModelCohereCommandR() {
    BedrockRuntimeClientBuilder builder = BedrockRuntimeClient.builder();
    builder.overrideConfiguration(createOverrideConfigurationBuilder().build());
    configureClient(builder);
    BedrockRuntimeClient client = builder.build();

    String modelId = "cohere.command-r-v1:0";

    Document requestPayload =
        Document.mapBuilder()
            .putString("message", "Say this is a test")
            .putNumber("max_tokens", 10)
            .putNumber("temperature", 0.8f)
            .putNumber("p", 1)
            .putList("stop_sequences", singletonList(Document.fromString("|")))
            .build();

    SdkJsonGenerator generator = new SdkJsonGenerator(new JsonFactory(), "application/json");
    DocumentTypeJsonMarshaller marshaller = new DocumentTypeJsonMarshaller(generator);
    requestPayload.accept(marshaller);

    InvokeModelRequest request =
        InvokeModelRequest.builder()
            .modelId(modelId)
            .body(SdkBytes.fromByteArray(generator.getBytes()))
            .build();

    InvokeModelResponse response = client.invokeModel(request);

    JsonNode node = JsonNode.parser().parse(response.body().asByteArray());
    Document responsePayload = node.visit(new DocumentUnmarshaller());
    assertThat(responsePayload.asMap().get("text").asString())
        .isEqualTo("This is a test. How's it going?");

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("chat cohere.command-r-v1:0")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfying(
                                equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                                equalTo(
                                    GEN_AI_OPERATION_NAME,
                                    GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues
                                        .CHAT),
                                equalTo(GEN_AI_REQUEST_MODEL, modelId),
                                equalTo(GEN_AI_REQUEST_MAX_TOKENS, 10),
                                satisfies(
                                    GEN_AI_REQUEST_TEMPERATURE,
                                    temp -> temp.isCloseTo(0.8, within(0.0001))),
                                equalTo(GEN_AI_REQUEST_TOP_P, 1.0),
                                equalTo(GEN_AI_REQUEST_STOP_SEQUENCES, asList("|")),
                                equalTo(GEN_AI_USAGE_INPUT_TOKENS, 3),
                                equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 6),
                                equalTo(GEN_AI_RESPONSE_FINISH_REASONS, asList("MAX_TOKENS")))));
  }

  @Test
  void testInvokeModelCohereCommand() {
    BedrockRuntimeClientBuilder builder = BedrockRuntimeClient.builder();
    builder.overrideConfiguration(createOverrideConfigurationBuilder().build());
    configureClient(builder);
    BedrockRuntimeClient client = builder.build();

    String modelId = "cohere.command-light-text-v14";

    Document requestPayload =
        Document.mapBuilder()
            .putString("prompt", "Say this is a test")
            .putNumber("max_tokens", 10)
            .putNumber("temperature", 0.8f)
            .putNumber("p", 1)
            .putList("stop_sequences", singletonList(Document.fromString("|")))
            .build();

    SdkJsonGenerator generator = new SdkJsonGenerator(new JsonFactory(), "application/json");
    DocumentTypeJsonMarshaller marshaller = new DocumentTypeJsonMarshaller(generator);
    requestPayload.accept(marshaller);

    InvokeModelRequest request =
        InvokeModelRequest.builder()
            .modelId(modelId)
            .body(SdkBytes.fromByteArray(generator.getBytes()))
            .build();

    InvokeModelResponse response = client.invokeModel(request);

    JsonNode node = JsonNode.parser().parse(response.body().asByteArray());
    Document responsePayload = node.visit(new DocumentUnmarshaller());
    assertThat(
            responsePayload
                .asMap()
                .get("generations")
                .asList()
                .get(0)
                .asMap()
                .get("text")
                .asString())
        .isEqualTo("I would be more than happy to assist you with");

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("chat cohere.command-light-text-v14")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfying(
                                equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                                equalTo(
                                    GEN_AI_OPERATION_NAME,
                                    GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues
                                        .CHAT),
                                equalTo(GEN_AI_REQUEST_MODEL, modelId),
                                equalTo(GEN_AI_REQUEST_MAX_TOKENS, 10),
                                satisfies(
                                    GEN_AI_REQUEST_TEMPERATURE,
                                    temp -> temp.isCloseTo(0.8, within(0.0001))),
                                equalTo(GEN_AI_REQUEST_TOP_P, 1.0),
                                equalTo(GEN_AI_REQUEST_STOP_SEQUENCES, asList("|")),
                                equalTo(GEN_AI_USAGE_INPUT_TOKENS, 3),
                                equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 8),
                                equalTo(GEN_AI_RESPONSE_FINISH_REASONS, asList("MAX_TOKENS")))));
  }

  @Test
  void testInvokeModelMistralMistral() {
    BedrockRuntimeClientBuilder builder = BedrockRuntimeClient.builder();
    builder.overrideConfiguration(createOverrideConfigurationBuilder().build());
    configureClient(builder);
    BedrockRuntimeClient client = builder.build();

    String modelId = "mistral.mistral-7b-instruct-v0:2";

    Document requestPayload =
        Document.mapBuilder()
            .putString("prompt", "Say this is a test")
            .putNumber("max_tokens", 10)
            .putNumber("temperature", 0.8f)
            .putNumber("top_p", 1)
            .putList("stop", singletonList(Document.fromString("|")))
            .build();

    SdkJsonGenerator generator = new SdkJsonGenerator(new JsonFactory(), "application/json");
    DocumentTypeJsonMarshaller marshaller = new DocumentTypeJsonMarshaller(generator);
    requestPayload.accept(marshaller);

    InvokeModelRequest request =
        InvokeModelRequest.builder()
            .modelId(modelId)
            .body(SdkBytes.fromByteArray(generator.getBytes()))
            .build();

    InvokeModelResponse response = client.invokeModel(request);

    JsonNode node = JsonNode.parser().parse(response.body().asByteArray());
    Document responsePayload = node.visit(new DocumentUnmarshaller());
    assertThat(
            responsePayload.asMap().get("outputs").asList().get(0).asMap().get("text").asString())
        .isEqualTo(", say it just a test...\n");

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("chat mistral.mistral-7b-instruct-v0:2")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfying(
                                equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                                equalTo(
                                    GEN_AI_OPERATION_NAME,
                                    GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues
                                        .CHAT),
                                equalTo(GEN_AI_REQUEST_MODEL, modelId),
                                equalTo(GEN_AI_REQUEST_MAX_TOKENS, 10),
                                satisfies(
                                    GEN_AI_REQUEST_TEMPERATURE,
                                    temp -> temp.isCloseTo(0.8, within(0.0001))),
                                equalTo(GEN_AI_REQUEST_TOP_P, 1.0),
                                equalTo(GEN_AI_REQUEST_STOP_SEQUENCES, asList("|")),
                                equalTo(GEN_AI_USAGE_INPUT_TOKENS, 3),
                                equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 4),
                                equalTo(GEN_AI_RESPONSE_FINISH_REASONS, asList("length")))));
  }

  @Test
  void testInvokeModelWithResponseStreamAnthropicClaude()
      throws InterruptedException, ExecutionException {
    BedrockRuntimeAsyncClientBuilder builder = BedrockRuntimeAsyncClient.builder();
    builder.overrideConfiguration(createOverrideConfigurationBuilder().build());
    configureClient(builder);
    BedrockRuntimeAsyncClient client = configureBedrockRuntimeClient(builder.build());

    String modelId = "anthropic.claude-v2";

    Document requestPayload =
        Document.mapBuilder()
            .putList(
                "messages",
                singletonList(
                    Document.mapBuilder()
                        .putString("role", "user")
                        .putList(
                            "content",
                            singletonList(
                                Document.mapBuilder()
                                    // Long output string to trigger multiple chunks.
                                    .putString("text", "List out every country in the world")
                                    .putString("type", "text")
                                    .build()))
                        .build()))
            .putString("anthropic_version", "bedrock-2023-05-31")
            .putNumber("max_tokens", 10)
            .putNumber("temperature", 0.8f)
            .putNumber("top_p", 1)
            .putList("stop_sequences", singletonList(Document.fromString("|")))
            .build();

    SdkJsonGenerator generator = new SdkJsonGenerator(new JsonFactory(), "application/json");
    DocumentTypeJsonMarshaller marshaller = new DocumentTypeJsonMarshaller(generator);
    requestPayload.accept(marshaller);

    InvokeModelWithResponseStreamRequest request =
        InvokeModelWithResponseStreamRequest.builder()
            .modelId(modelId)
            .body(SdkBytes.fromByteArray(generator.getBytes()))
            .build();

    StringBuilder text = new StringBuilder();

    InvokeModelWithResponseStreamResponseHandler responseHandler =
        InvokeModelWithResponseStreamResponseHandler.builder()
            .subscriber(
                InvokeModelWithResponseStreamResponseHandler.Visitor.builder()
                    .onChunk(
                        chunk -> {
                          JsonNode node = JsonNode.parser().parse(chunk.bytes().asByteArray());
                          DocumentUnmarshaller unmarshaller = new DocumentUnmarshaller();
                          Document result = node.visit(unmarshaller);
                          Document delta = result.asMap().get("delta");
                          if (delta == null) {
                            return;
                          }
                          text.append(delta.asMap().get("text").asString());
                        })
                    .build())
            .build();

    client.invokeModelWithResponseStream(request, responseHandler).get();

    assertThat(text.toString()).contains("Unfortunately I do not have a complete list of every");

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("chat anthropic.claude-v2")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfying(
                                equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                                equalTo(
                                    GEN_AI_OPERATION_NAME,
                                    GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues
                                        .CHAT),
                                equalTo(GEN_AI_REQUEST_MODEL, modelId),
                                equalTo(GEN_AI_REQUEST_MAX_TOKENS, 10),
                                satisfies(
                                    GEN_AI_REQUEST_TEMPERATURE,
                                    temp -> temp.isCloseTo(0.8, within(0.0001))),
                                equalTo(GEN_AI_REQUEST_TOP_P, 1.0),
                                equalTo(GEN_AI_REQUEST_STOP_SEQUENCES, asList("|")),
                                equalTo(GEN_AI_USAGE_INPUT_TOKENS, 16),
                                equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 10),
                                equalTo(GEN_AI_RESPONSE_FINISH_REASONS, asList("max_tokens")))));

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
                                        .hasSum(16)
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
                                        .hasSum(10)
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

    SpanContext spanCtx = getTesting().waitForTraces(1).get(0).get(0).getSpanContext();

    getTesting()
        .waitAndAssertLogRecords(
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                        equalTo(EVENT_NAME, "gen_ai.user.message"))
                    .hasSpanContext(spanCtx)
                    .hasBody(
                        Value.of(
                            KeyValue.of(
                                "content", Value.of("List out every country in the world")))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK), equalTo(EVENT_NAME, "gen_ai.choice"))
                    .hasSpanContext(spanCtx)
                    .hasBody(
                        Value.of(
                            KeyValue.of("finish_reason", Value.of("max_tokens")),
                            KeyValue.of("index", Value.of(0)),
                            KeyValue.of("content", Value.of(text.toString())))));
  }

  @Test
  void testInvokeModelToolCallAmazonNova() {
    BedrockRuntimeClientBuilder builder = BedrockRuntimeClient.builder();
    builder.overrideConfiguration(createOverrideConfigurationBuilder().build());
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
                    .hasBody(
                        Value.of(
                            KeyValue.of(
                                "content",
                                Value.of(
                                    "What is the weather in Seattle and San Francisco today?")))),
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
                                        KeyValue.of(
                                            "arguments", Value.of("{\"location\":\"Seattle\"}")),
                                        KeyValue.of("id", Value.of(seattleToolUseId)),
                                        KeyValue.of("type", Value.of("function"))),
                                    Value.of(
                                        KeyValue.of("name", Value.of("get_current_weather")),
                                        KeyValue.of(
                                            "arguments",
                                            Value.of("{\"location\":\"San Francisco\"}")),
                                        KeyValue.of("id", Value.of(sanFranciscoToolUseId)),
                                        KeyValue.of("type", Value.of("function"))))),
                            KeyValue.of(
                                "content",
                                Value.of(
                                    "<thinking> To provide the current weather in both Seattle and San Francisco, I will use the \"get_current_weather\" tool twice, once for each city. I will need to specify the \"location\" argument for each call.</thinking>\n")))));

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
                    .hasBody(
                        Value.of(
                            KeyValue.of(
                                "content",
                                Value.of(
                                    "What is the weather in Seattle and San Francisco today?")))),
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
                                        KeyValue.of(
                                            "arguments", Value.of("{\"location\":\"Seattle\"}")),
                                        KeyValue.of("id", Value.of(seattleToolUseId)),
                                        KeyValue.of("type", Value.of("function"))),
                                    Value.of(
                                        KeyValue.of("name", Value.of("get_current_weather")),
                                        KeyValue.of(
                                            "arguments",
                                            Value.of("{\"location\":\"San Francisco\"}")),
                                        KeyValue.of("id", Value.of(sanFranciscoToolUseId)),
                                        KeyValue.of("type", Value.of("function"))))),
                            KeyValue.of(
                                "content",
                                Value.of(
                                    "<thinking> To provide the current weather in both Seattle and San Francisco, I will use the \"get_current_weather\" tool twice, once for each city. I will need to specify the \"location\" argument for each call.</thinking>\n")))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                        equalTo(EVENT_NAME, "gen_ai.tool.message"))
                    .hasSpanContext(spanCtx1)
                    .hasBody(
                        Value.of(
                            KeyValue.of("id", Value.of(seattleToolUseId)),
                            KeyValue.of(
                                "content", Value.of("{\"weather\":\"50 degrees and raining\"}")))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                        equalTo(EVENT_NAME, "gen_ai.tool.message"))
                    .hasSpanContext(spanCtx1)
                    .hasBody(
                        Value.of(
                            KeyValue.of("id", Value.of(sanFranciscoToolUseId)),
                            KeyValue.of(
                                "content", Value.of("{\"weather\":\"70 degrees and sunny\"}")))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK), equalTo(EVENT_NAME, "gen_ai.choice"))
                    .hasSpanContext(spanCtx1)
                    .hasBody(
                        Value.of(
                            KeyValue.of("finish_reason", Value.of("end_turn")),
                            KeyValue.of("index", Value.of(0)),
                            KeyValue.of(
                                "content",
                                Value.of(
                                    "<thinking> I have received the weather information for both cities. Now I will compile this information and present it to the user.</thinking>\n"
                                        + "\n"
                                        + "The current weather in Seattle is 50 degrees and it is raining. The current weather in San Francisco is 70 degrees and it is sunny.")))));
  }

  @Test
  void testInvokeModelWithResponseStreamToolCallAmazonNova()
      throws InterruptedException, ExecutionException {
    BedrockRuntimeAsyncClientBuilder builder = BedrockRuntimeAsyncClient.builder();
    builder.overrideConfiguration(createOverrideConfigurationBuilder().build());
    configureClient(builder);
    BedrockRuntimeAsyncClient client = configureBedrockRuntimeClient(builder.build());

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
                    .hasBody(
                        Value.of(
                            KeyValue.of(
                                "content",
                                Value.of(
                                    "What is the weather in Seattle and San Francisco today?")))),
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
                                        KeyValue.of(
                                            "arguments", Value.of("{\"location\":\"Seattle\"}")),
                                        KeyValue.of("id", Value.of(seattleToolUseId)),
                                        KeyValue.of("type", Value.of("function"))),
                                    Value.of(
                                        KeyValue.of("name", Value.of("get_current_weather")),
                                        KeyValue.of(
                                            "arguments",
                                            Value.of("{\"location\":\"San Francisco\"}")),
                                        KeyValue.of("id", Value.of(sanFranciscoToolUseId)),
                                        KeyValue.of("type", Value.of("function"))))),
                            KeyValue.of(
                                "content",
                                Value.of(
                                    "<thinking> To provide the current weather in Seattle and San Francisco today, I will need to use the \"get_current_weather\" tool twice, once for each city. I will need to specify the location for each call.</thinking>\n")))));

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
                    .hasBody(
                        Value.of(
                            KeyValue.of(
                                "content",
                                Value.of(
                                    "What is the weather in Seattle and San Francisco today?")))),
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
                                        KeyValue.of(
                                            "arguments", Value.of("{\"location\":\"Seattle\"}")),
                                        KeyValue.of("id", Value.of(seattleToolUseId)),
                                        KeyValue.of("type", Value.of("function"))),
                                    Value.of(
                                        KeyValue.of("name", Value.of("get_current_weather")),
                                        KeyValue.of(
                                            "arguments",
                                            Value.of("{\"location\":\"San Francisco\"}")),
                                        KeyValue.of("id", Value.of(sanFranciscoToolUseId)),
                                        KeyValue.of("type", Value.of("function"))))),
                            KeyValue.of(
                                "content",
                                Value.of(
                                    "<thinking> To provide the current weather in Seattle and San Francisco today, I will need to use the \"get_current_weather\" tool twice, once for each city. I will need to specify the location for each call.</thinking>\n")))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                        equalTo(EVENT_NAME, "gen_ai.tool.message"))
                    .hasSpanContext(spanCtx1)
                    .hasBody(
                        Value.of(
                            KeyValue.of("id", Value.of(seattleToolUseId)),
                            KeyValue.of(
                                "content", Value.of("{\"weather\":\"50 degrees and raining\"}")))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                        equalTo(EVENT_NAME, "gen_ai.tool.message"))
                    .hasSpanContext(spanCtx1)
                    .hasBody(
                        Value.of(
                            KeyValue.of("id", Value.of(sanFranciscoToolUseId)),
                            KeyValue.of(
                                "content", Value.of("{\"weather\":\"70 degrees and sunny\"}")))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK), equalTo(EVENT_NAME, "gen_ai.choice"))
                    .hasSpanContext(spanCtx1)
                    .hasBody(
                        Value.of(
                            KeyValue.of("finish_reason", Value.of("end_turn")),
                            KeyValue.of("index", Value.of(0)),
                            KeyValue.of(
                                "content",
                                Value.of(
                                    "<thinking> I have received the weather information for both cities. Now I will compile this information into a clear response for the User.</thinking>\n"
                                        + "\n"
                                        + "The current weather in Seattle is 50 degrees and it is raining. In San Francisco, the weather is 70 degrees and sunny.")))));
  }

  @Test
  void testInvokeModelToolCallAnthropicClaude() {
    BedrockRuntimeClientBuilder builder = BedrockRuntimeClient.builder();
    builder.overrideConfiguration(createOverrideConfigurationBuilder().build());
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
                    .hasBody(
                        Value.of(
                            KeyValue.of(
                                "content",
                                Value.of(
                                    "What is the weather in Seattle and San Francisco today?")))),
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
                                        KeyValue.of(
                                            "arguments", Value.of("{\"location\":\"Seattle\"}")),
                                        KeyValue.of("id", Value.of(seattleToolUseId)),
                                        KeyValue.of("type", Value.of("function"))),
                                    Value.of(
                                        KeyValue.of("name", Value.of("get_current_weather")),
                                        KeyValue.of(
                                            "arguments",
                                            Value.of("{\"location\":\"San Francisco\"}")),
                                        KeyValue.of("id", Value.of(sanFranciscoToolUseId)),
                                        KeyValue.of("type", Value.of("function"))))),
                            KeyValue.of(
                                "content",
                                Value.of(
                                    "To answer your question about the weather in Seattle and San Francisco today, I'll need to use the get_current_weather function for both cities. Let me do that for you now.")))));

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
                    .hasBody(
                        Value.of(
                            KeyValue.of(
                                "content",
                                Value.of(
                                    "What is the weather in Seattle and San Francisco today?")))),
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
                                        KeyValue.of(
                                            "arguments", Value.of("{\"location\":\"Seattle\"}")),
                                        KeyValue.of("id", Value.of(seattleToolUseId)),
                                        KeyValue.of("type", Value.of("function"))),
                                    Value.of(
                                        KeyValue.of("name", Value.of("get_current_weather")),
                                        KeyValue.of(
                                            "arguments",
                                            Value.of("{\"location\":\"San Francisco\"}")),
                                        KeyValue.of("id", Value.of(sanFranciscoToolUseId)),
                                        KeyValue.of("type", Value.of("function"))))),
                            KeyValue.of(
                                "content",
                                Value.of(
                                    "To answer your question about the weather in Seattle and San Francisco today, I'll need to use the get_current_weather function for both cities. Let me do that for you now.")))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                        equalTo(EVENT_NAME, "gen_ai.tool.message"))
                    .hasSpanContext(spanCtx1)
                    .hasBody(
                        Value.of(
                            KeyValue.of("id", Value.of(seattleToolUseId)),
                            KeyValue.of("content", Value.of("\"50 degrees and raining\"")))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                        equalTo(EVENT_NAME, "gen_ai.tool.message"))
                    .hasSpanContext(spanCtx1)
                    .hasBody(
                        Value.of(
                            KeyValue.of("id", Value.of(sanFranciscoToolUseId)),
                            KeyValue.of("content", Value.of("\"70 degrees and sunny\"")))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK), equalTo(EVENT_NAME, "gen_ai.choice"))
                    .hasSpanContext(spanCtx1)
                    .hasBody(
                        Value.of(
                            KeyValue.of("finish_reason", Value.of("end_turn")),
                            KeyValue.of("index", Value.of(0)),
                            KeyValue.of(
                                "content",
                                Value.of(
                                    "Thank you for your patience. I've retrieved the current weather information for both cities. Here's the weather report for Seattle and San Francisco today:\n"
                                        + "\n"
                                        + "Seattle: 50 degrees and raining\n"
                                        + "San Francisco: 70 degrees and sunny\n"
                                        + "\n"
                                        + "As you can see, the weather conditions are quite different in these two cities today. Seattle is experiencing cooler temperatures with rain, which is fairly typical for the Pacific Northwest. On the other hand, San Francisco is enjoying a warmer, sunny day, which is pleasant for outdoor activities.\n"
                                        + "\n"
                                        + "Is there anything else you'd like to know about the weather in these or any other cities?")))));
  }

  @Test
  void testInvokeModelWithResponseStreamToolCallAnthropicClaude()
      throws InterruptedException, ExecutionException {
    BedrockRuntimeAsyncClientBuilder builder = BedrockRuntimeAsyncClient.builder();
    builder.overrideConfiguration(createOverrideConfigurationBuilder().build());
    configureClient(builder);
    BedrockRuntimeAsyncClient client = configureBedrockRuntimeClient(builder.build());

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
                    .hasBody(
                        Value.of(
                            KeyValue.of(
                                "content",
                                Value.of(
                                    "What is the weather in Seattle and San Francisco today?")))),
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
                                        KeyValue.of(
                                            "arguments", Value.of("{\"location\":\"Seattle\"}")),
                                        KeyValue.of("id", Value.of(seattleToolUseId)),
                                        KeyValue.of("type", Value.of("function"))),
                                    Value.of(
                                        KeyValue.of("name", Value.of("get_current_weather")),
                                        KeyValue.of(
                                            "arguments",
                                            Value.of("{\"location\":\"San Francisco\"}")),
                                        KeyValue.of("id", Value.of(sanFranciscoToolUseId)),
                                        KeyValue.of("type", Value.of("function"))))),
                            KeyValue.of(
                                "content",
                                Value.of(
                                    "To get the current weather for both Seattle and San Francisco, I'll need to use the get_current_weather function for each city. I'll make two separate calls within the same function calls block since these requests are independent of each other.")))));

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
                    .hasBody(
                        Value.of(
                            KeyValue.of(
                                "content",
                                Value.of(
                                    "What is the weather in Seattle and San Francisco today?")))),
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
                                        KeyValue.of(
                                            "arguments", Value.of("{\"location\":\"Seattle\"}")),
                                        KeyValue.of("id", Value.of(seattleToolUseId)),
                                        KeyValue.of("type", Value.of("function"))),
                                    Value.of(
                                        KeyValue.of("name", Value.of("get_current_weather")),
                                        KeyValue.of(
                                            "arguments",
                                            Value.of("{\"location\":\"San Francisco\"}")),
                                        KeyValue.of("id", Value.of(sanFranciscoToolUseId)),
                                        KeyValue.of("type", Value.of("function"))))),
                            KeyValue.of(
                                "content",
                                Value.of(
                                    "To get the current weather for both Seattle and San Francisco, I'll need to use the get_current_weather function for each city. I'll make two separate calls within the same function calls block since these requests are independent of each other.")))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                        equalTo(EVENT_NAME, "gen_ai.tool.message"))
                    .hasSpanContext(spanCtx1)
                    .hasBody(
                        Value.of(
                            KeyValue.of("id", Value.of(seattleToolUseId)),
                            KeyValue.of("content", Value.of("\"50 degrees and raining\"")))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK),
                        equalTo(EVENT_NAME, "gen_ai.tool.message"))
                    .hasSpanContext(spanCtx1)
                    .hasBody(
                        Value.of(
                            KeyValue.of("id", Value.of(sanFranciscoToolUseId)),
                            KeyValue.of("content", Value.of("\"70 degrees and sunny\"")))),
            log ->
                log.hasAttributesSatisfyingExactly(
                        equalTo(GEN_AI_SYSTEM, AWS_BEDROCK), equalTo(EVENT_NAME, "gen_ai.choice"))
                    .hasSpanContext(spanCtx1)
                    .hasBody(
                        Value.of(
                            KeyValue.of("finish_reason", Value.of("end_turn")),
                            KeyValue.of("index", Value.of(0)),
                            KeyValue.of(
                                "content",
                                Value.of(
                                    "\n"
                                        + "\n"
                                        + "Based on the results from the weather function, I can provide you with the current weather information for both cities:\n"
                                        + "\n"
                                        + "Seattle: The current weather in Seattle is 50 degrees and raining.\n"
                                        + "\n"
                                        + "San Francisco: The current weather in San Francisco is 70 degrees and sunny.\n"
                                        + "\n"
                                        + "As you can see, there's quite a difference in the weather between these two West Coast cities today. Seattle is experiencing cooler temperatures with rain, which is fairly typical for the city. On the other hand, San Francisco is enjoying warmer weather with sunshine. If you're planning any activities in either of these cities, you might want to dress accordingly and perhaps bring an umbrella if you're in Seattle!")))));
  }
}
