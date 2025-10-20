/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.ai.openai.v1_0;

import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_INPUT_MESSAGES;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_OUTPUT_MESSAGES;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_PROVIDER_NAME;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_CHOICE_COUNT;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_FREQUENCY_PENALTY;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MAX_TOKENS;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_PRESENCE_PENALTY;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_SEED;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_STOP_SEQUENCES;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_TEMPERATURE;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_TOP_P;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_FINISH_REASONS;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_ID;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_MODEL;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_TOOL_DEFINITIONS;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_INPUT_TOKENS;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_OUTPUT_TOKENS;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues.CHAT;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GenAiProviderNameIncubatingValues.OPENAI;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

public abstract class AbstractChatCompletionTest extends AbstractSpringAiOpenaiTest {

  protected static final String TEST_CHAT_MODEL = "qwen3-coder-flash";
  protected static final String TEST_CHAT_INPUT =
      "Answer in up to 3 words: Which ocean contains Bouvet Island?";

  @Test
  void basic() {
    Prompt prompt =
        Prompt.builder()
            .messages(UserMessage.builder().text(TEST_CHAT_INPUT).build())
            .chatOptions(ChatOptions.builder().model(TEST_CHAT_MODEL).build())
            .build();
    OpenAiChatModel chatModel = getChatModel();

    ChatResponse response = chatModel.call(prompt);
    String content = "South Atlantic";
    assertThat(response.getResults().get(0).getOutput().getText()).isEqualTo(content);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasAttributesSatisfying(
                            equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                            equalTo(GEN_AI_OPERATION_NAME, CHAT),
                            equalTo(GEN_AI_REQUEST_MODEL, TEST_CHAT_MODEL),
                            equalTo(GEN_AI_REQUEST_TEMPERATURE, 0.7d),
                            equalTo(GEN_AI_RESPONSE_ID, response.getMetadata().getId()),
                            equalTo(GEN_AI_RESPONSE_MODEL, TEST_CHAT_MODEL),
                            satisfies(
                                GEN_AI_RESPONSE_FINISH_REASONS,
                                reasons -> reasons.containsExactly("stop")),
                            equalTo(GEN_AI_USAGE_INPUT_TOKENS, 23L),
                            equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 2L),
                            equalTo(GEN_AI_USAGE_TOTAL_TOKENS, 25L),
                            equalTo(GEN_AI_SPAN_KIND, "LLM"),
                            satisfies(GEN_AI_INPUT_MESSAGES, messages -> messages.contains("user")),
                            satisfies(
                                GEN_AI_INPUT_MESSAGES,
                                messages ->
                                    messages.contains(
                                        "Answer in up to 3 words: Which ocean contains Bouvet Island?")),
                            satisfies(
                                GEN_AI_OUTPUT_MESSAGES, messages -> messages.contains("assistant")),
                            satisfies(
                                GEN_AI_OUTPUT_MESSAGES,
                                messages -> messages.contains("South Atlantic")),
                            satisfies(
                                GEN_AI_OUTPUT_MESSAGES, messages -> messages.contains("stop")))));
  }

  @Test
  void stream() {
    Prompt prompt =
        Prompt.builder()
            .messages(UserMessage.builder().text(TEST_CHAT_INPUT).build())
            .chatOptions(ChatOptions.builder().model(TEST_CHAT_MODEL).build())
            .build();
    OpenAiChatModel chatModel = getChatModel();

    List<ChatResponse> chunks = chatModel.stream(prompt).collectList().block();

    String fullMessage =
        chunks.stream()
            .map(
                cc -> {
                  if (cc.getResults().isEmpty()) {
                    return Optional.<String>empty();
                  }
                  return Optional.of(cc.getResults().get(0).getOutput().getText());
                })
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.joining());

    String content = "South Atlantic";
    assertEquals(fullMessage, content);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasAttributesSatisfying(
                            equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                            equalTo(GEN_AI_OPERATION_NAME, CHAT),
                            equalTo(GEN_AI_REQUEST_MODEL, TEST_CHAT_MODEL),
                            equalTo(GEN_AI_REQUEST_TEMPERATURE, 0.7d),
                            equalTo(GEN_AI_RESPONSE_ID, chunks.get(0).getMetadata().getId()),
                            satisfies(
                                GEN_AI_RESPONSE_FINISH_REASONS,
                                reasons -> reasons.containsExactly("stop")),
                            equalTo(GEN_AI_SPAN_KIND, "LLM"),
                            satisfies(GEN_AI_INPUT_MESSAGES, messages -> messages.contains("user")),
                            satisfies(
                                GEN_AI_INPUT_MESSAGES,
                                messages ->
                                    messages.contains(
                                        "Answer in up to 3 words: Which ocean contains Bouvet Island?")),
                            satisfies(
                                GEN_AI_OUTPUT_MESSAGES, messages -> messages.contains("assistant")),
                            satisfies(
                                GEN_AI_OUTPUT_MESSAGES,
                                messages -> messages.contains("South Atlantic")),
                            satisfies(
                                GEN_AI_OUTPUT_MESSAGES, messages -> messages.contains("stop")))));
  }

  @Test
  void allTheClientOptions() {
    OpenAiChatOptions options =
        OpenAiChatOptions.builder()
            .model(TEST_CHAT_MODEL)
            .maxTokens(1000)
            .seed(100)
            .stop(singletonList("foo"))
            .topP(1.0)
            .temperature(0.8)
            .frequencyPenalty(0.5)
            .presencePenalty(0.3)
            .build();
    Prompt prompt =
        Prompt.builder()
            .messages(UserMessage.builder().text(TEST_CHAT_INPUT).build())
            .chatOptions(options)
            .build();
    OpenAiChatModel chatModel = getChatModel();

    ChatResponse response = chatModel.call(prompt);
    String content = "Southern Ocean";
    assertThat(response.getResults().get(0).getOutput().getText()).isEqualTo(content);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasAttributesSatisfying(
                            equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                            equalTo(GEN_AI_OPERATION_NAME, CHAT),
                            equalTo(GEN_AI_REQUEST_MODEL, TEST_CHAT_MODEL),
                            equalTo(GEN_AI_REQUEST_TEMPERATURE, 0.8d),
                            equalTo(
                                GEN_AI_REQUEST_MAX_TOKENS, Long.valueOf(options.getMaxTokens())),
                            equalTo(GEN_AI_REQUEST_SEED, Long.valueOf(options.getSeed())),
                            satisfies(
                                GEN_AI_REQUEST_STOP_SEQUENCES,
                                seq -> seq.hasSize(options.getStop().size())),
                            equalTo(GEN_AI_REQUEST_TOP_P, options.getTopP()),
                            equalTo(
                                GEN_AI_REQUEST_FREQUENCY_PENALTY, options.getFrequencyPenalty()),
                            equalTo(GEN_AI_REQUEST_PRESENCE_PENALTY, options.getPresencePenalty()),
                            equalTo(GEN_AI_RESPONSE_ID, response.getMetadata().getId()),
                            equalTo(GEN_AI_RESPONSE_MODEL, TEST_CHAT_MODEL),
                            satisfies(
                                GEN_AI_RESPONSE_FINISH_REASONS,
                                reasons -> reasons.containsExactly("stop")),
                            equalTo(GEN_AI_USAGE_INPUT_TOKENS, 23L),
                            equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 2L),
                            equalTo(GEN_AI_USAGE_TOTAL_TOKENS, 25L),
                            equalTo(GEN_AI_SPAN_KIND, "LLM"),
                            satisfies(GEN_AI_INPUT_MESSAGES, messages -> messages.contains("user")),
                            satisfies(
                                GEN_AI_INPUT_MESSAGES,
                                messages ->
                                    messages.contains(
                                        "Answer in up to 3 words: Which ocean contains Bouvet Island?")),
                            satisfies(
                                GEN_AI_OUTPUT_MESSAGES, messages -> messages.contains("assistant")),
                            satisfies(
                                GEN_AI_OUTPUT_MESSAGES,
                                messages -> messages.contains("Southern Ocean")),
                            satisfies(
                                GEN_AI_OUTPUT_MESSAGES, messages -> messages.contains("stop")))));
  }

  @Test
  void with400Error() {
    Prompt prompt =
        Prompt.builder()
            .messages(UserMessage.builder().text(TEST_CHAT_INPUT).build())
            .chatOptions(ChatOptions.builder().model("gpt-4o").build())
            .build();
    OpenAiChatModel chatModel = getChatModel();

    Throwable thrown = catchThrowable(() -> chatModel.stream(prompt).collectList().block());
    assertThat(thrown).isInstanceOf(Exception.class);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasAttributesSatisfying(
                            equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                            equalTo(GEN_AI_OPERATION_NAME, CHAT),
                            equalTo(GEN_AI_REQUEST_MODEL, "gpt-4o"),
                            equalTo(GEN_AI_REQUEST_TEMPERATURE, 0.7d),
                            equalTo(GEN_AI_SPAN_KIND, "LLM"),
                            satisfies(GEN_AI_INPUT_MESSAGES, messages -> messages.contains("user")),
                            satisfies(
                                GEN_AI_INPUT_MESSAGES,
                                messages ->
                                    messages.contains(
                                        "Answer in up to 3 words: Which ocean contains Bouvet Island?")))));
  }

  @Test
  void multipleChoices() {
    Prompt prompt =
        Prompt.builder()
            .messages(UserMessage.builder().text(TEST_CHAT_INPUT).build())
            .chatOptions(OpenAiChatOptions.builder().model(TEST_CHAT_MODEL).N(2).build())
            .build();
    OpenAiChatModel chatModel = getChatModel();

    ChatResponse response = chatModel.call(prompt);
    String content1 = "Southern Ocean";
    assertThat(response.getResults().get(0).getOutput().getText()).isEqualTo(content1);
    String content2 = "South";
    assertThat(response.getResults().get(1).getOutput().getText()).isEqualTo(content2);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasAttributesSatisfying(
                            equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                            equalTo(GEN_AI_OPERATION_NAME, CHAT),
                            equalTo(GEN_AI_REQUEST_MODEL, TEST_CHAT_MODEL),
                            equalTo(GEN_AI_REQUEST_TEMPERATURE, 0.7d),
                            equalTo(GEN_AI_RESPONSE_ID, response.getMetadata().getId()),
                            equalTo(GEN_AI_RESPONSE_MODEL, TEST_CHAT_MODEL),
                            equalTo(GEN_AI_REQUEST_CHOICE_COUNT, 2),
                            satisfies(
                                GEN_AI_RESPONSE_FINISH_REASONS,
                                reasons -> reasons.containsExactly("stop", "stop")),
                            equalTo(GEN_AI_USAGE_INPUT_TOKENS, 23L),
                            equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 3L),
                            equalTo(GEN_AI_USAGE_TOTAL_TOKENS, 26L),
                            equalTo(GEN_AI_SPAN_KIND, "LLM"),
                            satisfies(GEN_AI_INPUT_MESSAGES, messages -> messages.contains("user")),
                            satisfies(
                                GEN_AI_INPUT_MESSAGES,
                                messages ->
                                    messages.contains(
                                        "Answer in up to 3 words: Which ocean contains Bouvet Island?")),
                            satisfies(
                                GEN_AI_OUTPUT_MESSAGES, messages -> messages.contains("assistant")),
                            satisfies(
                                GEN_AI_OUTPUT_MESSAGES,
                                messages -> messages.contains("Southern Ocean")),
                            satisfies(
                                GEN_AI_OUTPUT_MESSAGES, messages -> messages.contains("stop")))));
  }

  @Test
  void streamMultipleChoices() {
    Prompt prompt =
        Prompt.builder()
            .messages(UserMessage.builder().text(TEST_CHAT_INPUT).build())
            .chatOptions(OpenAiChatOptions.builder().model(TEST_CHAT_MODEL).N(2).build())
            .build();
    OpenAiChatModel chatModel = getChatModel();

    // there's a bug in open-ai chat model, thus we couldn't agg multi choice
    List<ChatResponse> chunks = chatModel.stream(prompt).collectList().block();

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasAttributesSatisfying(
                            equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                            equalTo(GEN_AI_OPERATION_NAME, CHAT),
                            equalTo(GEN_AI_REQUEST_MODEL, TEST_CHAT_MODEL),
                            equalTo(GEN_AI_REQUEST_TEMPERATURE, 0.7d),
                            equalTo(GEN_AI_RESPONSE_ID, chunks.get(0).getMetadata().getId()),
                            equalTo(GEN_AI_REQUEST_CHOICE_COUNT, 2),
                            satisfies(
                                GEN_AI_RESPONSE_FINISH_REASONS,
                                reasons -> reasons.containsExactly("stop", "stop")),
                            equalTo(GEN_AI_SPAN_KIND, "LLM"),
                            satisfies(GEN_AI_INPUT_MESSAGES, messages -> messages.contains("user")),
                            satisfies(
                                GEN_AI_INPUT_MESSAGES,
                                messages ->
                                    messages.contains(
                                        "Answer in up to 3 words: Which ocean contains Bouvet Island?")),
                            satisfies(
                                GEN_AI_OUTPUT_MESSAGES, messages -> messages.contains("assistant")),
                            satisfies(
                                GEN_AI_OUTPUT_MESSAGES,
                                messages -> messages.contains("Southern Ocean")),
                            satisfies(
                                GEN_AI_OUTPUT_MESSAGES, messages -> messages.contains("stop")))));
  }

  @Test
  void toolCalls() {
    Prompt prompt =
        Prompt.builder()
            .messages(
                asList(
                    SystemMessage.builder()
                        .text("You are a helpful assistant providing weather updates.")
                        .build(),
                    UserMessage.builder()
                        .text("What is the weather in New York City and London?")
                        .build()))
            .chatOptions(
                OpenAiChatOptions.builder()
                    .model(TEST_CHAT_MODEL)
                    .toolCallbacks(buildGetWeatherToolDefinition())
                    .build())
            .build();
    OpenAiChatModel chatModel = getChatModel();

    ChatResponse response = chatModel.call(prompt);

    List<ToolCall> toolCalls = response.getResult().getOutput().getToolCalls();

    assertThat(toolCalls.get(0).id()).startsWith("call_");
    assertThat(toolCalls.get(1).id()).startsWith("call_");

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasAttributesSatisfying(
                            equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                            equalTo(GEN_AI_OPERATION_NAME, CHAT),
                            equalTo(GEN_AI_REQUEST_MODEL, TEST_CHAT_MODEL),
                            equalTo(GEN_AI_REQUEST_TEMPERATURE, 0.7d),
                            equalTo(GEN_AI_RESPONSE_MODEL, "qwen3-coder-flash"),
                            satisfies(GEN_AI_RESPONSE_ID, id -> id.startsWith("chatcmpl-")),
                            satisfies(
                                GEN_AI_RESPONSE_FINISH_REASONS,
                                reasons -> reasons.containsExactly("tool_calls")),
                            equalTo(GEN_AI_SPAN_KIND, "LLM"),
                            equalTo(GEN_AI_USAGE_INPUT_TOKENS, 311L),
                            equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 45L),
                            equalTo(GEN_AI_USAGE_TOTAL_TOKENS, 356L),
                            satisfies(GEN_AI_INPUT_MESSAGES, messages -> messages.contains("user")),
                            satisfies(
                                GEN_AI_INPUT_MESSAGES,
                                messages ->
                                    messages.contains(
                                        "What is the weather in New York City and London?")),
                            satisfies(
                                GEN_AI_INPUT_MESSAGES, messages -> messages.contains("system")),
                            satisfies(
                                GEN_AI_INPUT_MESSAGES,
                                messages ->
                                    messages.contains(
                                        "You are a helpful assistant providing weather updates.")),
                            satisfies(
                                GEN_AI_OUTPUT_MESSAGES, messages -> messages.contains("tool_call")),
                            satisfies(
                                GEN_AI_OUTPUT_MESSAGES,
                                messages -> messages.contains("get_weather")),
                            satisfies(
                                GEN_AI_OUTPUT_MESSAGES,
                                messages -> messages.contains("New York City")),
                            satisfies(
                                GEN_AI_OUTPUT_MESSAGES, messages -> messages.contains("London")),
                            satisfies(
                                GEN_AI_OUTPUT_MESSAGES,
                                messages -> messages.contains("tool_calls")),
                            satisfies(
                                GEN_AI_TOOL_DEFINITIONS, messages -> messages.contains("function")),
                            satisfies(
                                GEN_AI_TOOL_DEFINITIONS,
                                messages -> messages.contains("get_weather")))));

    getTesting().clearData();

    prompt =
        Prompt.builder()
            .messages(
                asList(
                    SystemMessage.builder()
                        .text("You are a helpful assistant providing weather updates.")
                        .build(),
                    UserMessage.builder()
                        .text("What is the weather in New York City and London?")
                        .build(),
                    response.getResult().getOutput(),
                    new ToolResponseMessage(
                        asList(
                            new ToolResponse(
                                toolCalls.get(0).id(), "get_weather", "25 degrees and sunny"),
                            new ToolResponse(
                                toolCalls.get(1).id(), "get_weather", "15 degrees and sunny")))))
            .chatOptions(
                OpenAiChatOptions.builder()
                    .model(TEST_CHAT_MODEL)
                    .toolCallbacks(buildGetWeatherToolDefinition())
                    .build())
            .build();

    response = chatModel.call(prompt);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasAttributesSatisfying(
                            equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                            equalTo(GEN_AI_OPERATION_NAME, CHAT),
                            equalTo(GEN_AI_REQUEST_MODEL, TEST_CHAT_MODEL),
                            satisfies(GEN_AI_RESPONSE_ID, id -> id.startsWith("chatcmpl-")),
                            equalTo(GEN_AI_REQUEST_TEMPERATURE, 0.7d),
                            equalTo(GEN_AI_RESPONSE_MODEL, "qwen3-coder-flash"),
                            satisfies(
                                GEN_AI_RESPONSE_FINISH_REASONS,
                                reasons -> reasons.containsExactly("stop")),
                            equalTo(GEN_AI_SPAN_KIND, "LLM"),
                            equalTo(GEN_AI_USAGE_INPUT_TOKENS, 386L),
                            equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 31L),
                            equalTo(GEN_AI_USAGE_TOTAL_TOKENS, 417L),
                            satisfies(
                                GEN_AI_INPUT_MESSAGES,
                                messages -> messages.contains("tool_call_response")),
                            satisfies(
                                GEN_AI_INPUT_MESSAGES,
                                messages -> messages.contains("25 degrees and sunny")),
                            satisfies(
                                GEN_AI_INPUT_MESSAGES,
                                messages -> messages.contains("15 degrees and sunny")),
                            satisfies(
                                GEN_AI_OUTPUT_MESSAGES, messages -> messages.contains("assistant")),
                            satisfies(
                                GEN_AI_OUTPUT_MESSAGES, messages -> messages.contains("stop")),
                            satisfies(
                                GEN_AI_TOOL_DEFINITIONS, messages -> messages.contains("function")),
                            satisfies(
                                GEN_AI_TOOL_DEFINITIONS,
                                messages -> messages.contains("get_weather")))));
  }

  @Test
  void streamToolCalls() {
    Prompt prompt =
        Prompt.builder()
            .messages(
                asList(
                    SystemMessage.builder()
                        .text("You are a helpful assistant providing weather updates.")
                        .build(),
                    UserMessage.builder()
                        .text("What is the weather in New York City and London?")
                        .build()))
            .chatOptions(
                OpenAiChatOptions.builder()
                    .model(TEST_CHAT_MODEL)
                    .toolCallbacks(buildGetWeatherToolDefinition())
                    .build())
            .build();
    OpenAiChatModel chatModel = getChatModel();

    List<ChatResponse> chunks = chatModel.stream(prompt).toStream().collect(Collectors.toList());

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasAttributesSatisfying(
                            equalTo(GEN_AI_PROVIDER_NAME, OPENAI),
                            equalTo(GEN_AI_OPERATION_NAME, CHAT),
                            equalTo(GEN_AI_REQUEST_MODEL, TEST_CHAT_MODEL),
                            equalTo(GEN_AI_REQUEST_TEMPERATURE, 0.7d),
                            equalTo(GEN_AI_RESPONSE_ID, chunks.get(0).getMetadata().getId()),
                            satisfies(
                                GEN_AI_RESPONSE_FINISH_REASONS,
                                reasons -> reasons.containsExactly("tool_calls")),
                            equalTo(GEN_AI_SPAN_KIND, "LLM"),
                            satisfies(GEN_AI_INPUT_MESSAGES, messages -> messages.contains("user")),
                            satisfies(
                                GEN_AI_INPUT_MESSAGES,
                                messages ->
                                    messages.contains(
                                        "What is the weather in New York City and London?")),
                            satisfies(
                                GEN_AI_INPUT_MESSAGES, messages -> messages.contains("system")),
                            satisfies(
                                GEN_AI_INPUT_MESSAGES,
                                messages ->
                                    messages.contains(
                                        "You are a helpful assistant providing weather updates.")),
                            satisfies(
                                GEN_AI_OUTPUT_MESSAGES, messages -> messages.contains("tool_call")),
                            satisfies(
                                GEN_AI_OUTPUT_MESSAGES,
                                messages -> messages.contains("get_weather")),
                            satisfies(
                                GEN_AI_OUTPUT_MESSAGES,
                                messages -> messages.contains("New York City")),
                            satisfies(
                                GEN_AI_OUTPUT_MESSAGES, messages -> messages.contains("London")),
                            satisfies(
                                GEN_AI_OUTPUT_MESSAGES,
                                messages -> messages.contains("tool_calls")),
                            satisfies(
                                GEN_AI_TOOL_DEFINITIONS, messages -> messages.contains("function")),
                            satisfies(
                                GEN_AI_TOOL_DEFINITIONS,
                                messages -> messages.contains("get_weather")))));
  }

  private ToolCallback buildGetWeatherToolDefinition() {
    return FunctionToolCallback.builder("get_weather", new GetWeatherFunction())
        .description("The location to get the current temperature for")
        .inputType(ToolInput.class)
        .build();
  }

  public static class ToolInput {
    private String location;

    public String getLocation() {
      return location;
    }

    public ToolInput(String location) {
      this.location = location;
    }
  }

  private static class GetWeatherFunction implements Function<ToolInput, String> {
    @Override
    public String apply(ToolInput location) {
      return "test function";
    }
  }
}
