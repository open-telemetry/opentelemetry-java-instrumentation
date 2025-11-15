/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.ai.v1_0;

import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.AgentIncubatingAttributes.GEN_AI_AGENT_NAME;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_INPUT_MESSAGES;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_OUTPUT_MESSAGES;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_PROVIDER_NAME;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_FINISH_REASONS;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_ID;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_MODEL;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_TOOL_DEFINITIONS;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_INPUT_TOKENS;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_OUTPUT_TOKENS;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues.CHAT;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues.EXECUTE_TOOL;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues.INVOKE_AGENT;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiToolIncubatingAttributes.GEN_AI_TOOL_DESCRIPTION;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiToolIncubatingAttributes.GEN_AI_TOOL_NAME;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiToolIncubatingAttributes.GEN_AI_TOOL_TYPE;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

public abstract class AbstractChatClientTest extends AbstractSpringAiTest {

  protected static final String TEST_CHAT_MODEL = "qwen3-coder-flash";
  protected static final String TEST_CHAT_INPUT =
      "Answer in up to 3 words: Which ocean contains Bouvet Island?";
  protected static final String TEST_AGENT_NAME = "spring_ai chat_client";
  protected static final String TEST_TOOL_NAME = "get_weather";

  @Test
  void basic() {
    Prompt prompt =
        Prompt.builder()
            .messages(UserMessage.builder().text(TEST_CHAT_INPUT).build())
            .chatOptions(ChatOptions.builder().model(TEST_CHAT_MODEL).build())
            .build();
    ChatClient chatClient = getChatClient();

    ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
    String content = "Southern Ocean";
    assertThat(response.getResults().get(0).getOutput().getText()).isEqualTo(content);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(INVOKE_AGENT + " " + TEST_AGENT_NAME)
                            .hasAttributesSatisfying(
                                equalTo(GEN_AI_AGENT_NAME, TEST_AGENT_NAME),
                                equalTo(GEN_AI_PROVIDER_NAME, "spring-ai"),
                                equalTo(GEN_AI_OPERATION_NAME, INVOKE_AGENT),
                                equalTo(GEN_AI_REQUEST_MODEL, TEST_CHAT_MODEL),
                                equalTo(GEN_AI_RESPONSE_MODEL, TEST_CHAT_MODEL),
                                satisfies(
                                    GEN_AI_RESPONSE_FINISH_REASONS,
                                    reasons -> reasons.containsExactly("stop")),
                                equalTo(GEN_AI_USAGE_INPUT_TOKENS, 23L),
                                equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 2L),
                                equalTo(GEN_AI_USAGE_TOTAL_TOKENS, 25L),
                                equalTo(GEN_AI_SPAN_KIND, "AGENT"),
                                satisfies(
                                    GEN_AI_INPUT_MESSAGES, messages -> messages.contains("user")),
                                satisfies(
                                    GEN_AI_INPUT_MESSAGES,
                                    messages ->
                                        messages.contains(
                                            "Answer in up to 3 words: Which ocean contains Bouvet Island?")),
                                satisfies(
                                    GEN_AI_OUTPUT_MESSAGES,
                                    messages -> messages.contains("assistant")),
                                satisfies(
                                    GEN_AI_OUTPUT_MESSAGES,
                                    messages -> messages.contains("Southern Ocean")),
                                satisfies(
                                    GEN_AI_OUTPUT_MESSAGES, messages -> messages.contains("stop"))),
                    span ->
                        span.hasName(CHAT + " " + TEST_CHAT_MODEL)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfying(
                                equalTo(GEN_AI_OPERATION_NAME, CHAT),
                                equalTo(GEN_AI_SPAN_KIND, "LLM"))));
  }

  @Test
  void stream() {
    Prompt prompt =
        Prompt.builder()
            .messages(UserMessage.builder().text(TEST_CHAT_INPUT).build())
            .chatOptions(ChatOptions.builder().model(TEST_CHAT_MODEL).build())
            .build();
    ChatClient chatClient = getChatClient();

    List<ChatResponse> chunks =
        chatClient.prompt(prompt).stream().chatResponse().toStream().collect(Collectors.toList());

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
                        span.hasName(INVOKE_AGENT + " " + TEST_AGENT_NAME)
                            .hasAttributesSatisfying(
                                equalTo(GEN_AI_AGENT_NAME, TEST_AGENT_NAME),
                                equalTo(GEN_AI_PROVIDER_NAME, "spring-ai"),
                                equalTo(GEN_AI_OPERATION_NAME, INVOKE_AGENT),
                                equalTo(GEN_AI_REQUEST_MODEL, TEST_CHAT_MODEL),
                                equalTo(GEN_AI_RESPONSE_ID, chunks.get(0).getMetadata().getId()),
                                satisfies(
                                    GEN_AI_RESPONSE_FINISH_REASONS,
                                    reasons -> reasons.containsExactly("stop")),
                                equalTo(GEN_AI_SPAN_KIND, "AGENT"),
                                satisfies(
                                    GEN_AI_INPUT_MESSAGES, messages -> messages.contains("user")),
                                satisfies(
                                    GEN_AI_INPUT_MESSAGES,
                                    messages ->
                                        messages.contains(
                                            "Answer in up to 3 words: Which ocean contains Bouvet Island?")),
                                satisfies(
                                    GEN_AI_OUTPUT_MESSAGES,
                                    messages -> messages.contains("assistant")),
                                satisfies(
                                    GEN_AI_OUTPUT_MESSAGES,
                                    messages -> messages.contains("South Atlantic")),
                                satisfies(
                                    GEN_AI_OUTPUT_MESSAGES, messages -> messages.contains("stop"))),
                    span ->
                        span.hasName(CHAT + " " + TEST_CHAT_MODEL)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfying(
                                equalTo(GEN_AI_OPERATION_NAME, CHAT),
                                equalTo(GEN_AI_SPAN_KIND, "LLM"))));
  }

  @Test
  void with400Error() {
    Prompt prompt =
        Prompt.builder()
            .messages(UserMessage.builder().text(TEST_CHAT_INPUT).build())
            .chatOptions(ChatOptions.builder().model("gpt-4o").build())
            .build();
    ChatClient chatClient = getChatClient();

    Throwable thrown = catchThrowable(() -> chatClient.prompt(prompt).call().chatResponse());
    assertThat(thrown).isInstanceOf(Exception.class);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasStatus(StatusData.error())
                            .hasName(INVOKE_AGENT + " " + TEST_AGENT_NAME)
                            .hasAttributesSatisfying(
                                equalTo(GEN_AI_AGENT_NAME, TEST_AGENT_NAME),
                                equalTo(GEN_AI_PROVIDER_NAME, "spring-ai"),
                                equalTo(GEN_AI_OPERATION_NAME, INVOKE_AGENT),
                                equalTo(GEN_AI_REQUEST_MODEL, "gpt-4o"),
                                equalTo(GEN_AI_SPAN_KIND, "AGENT"),
                                satisfies(
                                    GEN_AI_INPUT_MESSAGES, messages -> messages.contains("user")),
                                satisfies(
                                    GEN_AI_INPUT_MESSAGES,
                                    messages ->
                                        messages.contains(
                                            "Answer in up to 3 words: Which ocean contains Bouvet Island?")))));
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
                    .toolCallbacks(getToolCallbacks())
                    .build())
            .build();

    ChatClient chatClient = getChatClient();

    ChatResponse response = chatClient.prompt(prompt).call().chatResponse();

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(INVOKE_AGENT + " " + TEST_AGENT_NAME)
                            .hasAttributesSatisfying(
                                equalTo(GEN_AI_AGENT_NAME, TEST_AGENT_NAME),
                                equalTo(GEN_AI_PROVIDER_NAME, "spring-ai"),
                                equalTo(GEN_AI_OPERATION_NAME, INVOKE_AGENT),
                                equalTo(GEN_AI_REQUEST_MODEL, TEST_CHAT_MODEL),
                                equalTo(GEN_AI_RESPONSE_ID, response.getMetadata().getId()),
                                equalTo(GEN_AI_RESPONSE_MODEL, TEST_CHAT_MODEL),
                                satisfies(
                                    GEN_AI_RESPONSE_FINISH_REASONS,
                                    reasons -> reasons.containsExactly("stop")),
                                equalTo(GEN_AI_USAGE_INPUT_TOKENS, 739L),
                                equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 76L),
                                equalTo(GEN_AI_USAGE_TOTAL_TOKENS, 815L),
                                equalTo(GEN_AI_SPAN_KIND, "AGENT"),
                                satisfies(
                                    GEN_AI_INPUT_MESSAGES, messages -> messages.contains("system")),
                                satisfies(
                                    GEN_AI_INPUT_MESSAGES,
                                    messages ->
                                        messages.contains(
                                            "You are a helpful assistant providing weather updates.")),
                                satisfies(
                                    GEN_AI_INPUT_MESSAGES, messages -> messages.contains("user")),
                                satisfies(
                                    GEN_AI_INPUT_MESSAGES,
                                    messages ->
                                        messages.contains(
                                            "What is the weather in New York City and London?")),
                                satisfies(
                                    GEN_AI_OUTPUT_MESSAGES,
                                    messages -> messages.contains("assistant")),
                                satisfies(
                                    GEN_AI_OUTPUT_MESSAGES,
                                    messages ->
                                        messages.contains(
                                            "The current weather is as follows:\\n- **New York City**: 25 degrees and sunny.\\n- **London**: 15 degrees and raining.")),
                                satisfies(
                                    GEN_AI_OUTPUT_MESSAGES, messages -> messages.contains("stop")),
                                satisfies(
                                    GEN_AI_TOOL_DEFINITIONS,
                                    messages -> messages.contains("function")),
                                satisfies(
                                    GEN_AI_TOOL_DEFINITIONS,
                                    messages -> messages.contains("get_weather")),
                                satisfies(
                                    GEN_AI_TOOL_DEFINITIONS,
                                    messages ->
                                        messages.contains(
                                            "The location to get the current temperature for"))),
                    span ->
                        span.hasName(CHAT + " " + TEST_CHAT_MODEL)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfying(
                                equalTo(GEN_AI_OPERATION_NAME, CHAT),
                                satisfies(
                                    GEN_AI_RESPONSE_FINISH_REASONS,
                                    reasons -> reasons.containsExactly("tool_calls")),
                                equalTo(GEN_AI_SPAN_KIND, "LLM"),
                                equalTo(GEN_AI_USAGE_INPUT_TOKENS, 331L),
                                equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 45L),
                                equalTo(GEN_AI_USAGE_TOTAL_TOKENS, 376L)),
                    // 2 spans are compressed into 1 span
                    span ->
                        span.hasName(EXECUTE_TOOL + " " + TEST_TOOL_NAME)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfying(
                                equalTo(GEN_AI_OPERATION_NAME, EXECUTE_TOOL),
                                equalTo(GEN_AI_SPAN_KIND, "TOOL"),
                                equalTo(
                                    GEN_AI_TOOL_DESCRIPTION,
                                    "The location to get the current temperature for"),
                                equalTo(GEN_AI_TOOL_TYPE, "function"),
                                equalTo(GEN_AI_TOOL_NAME, TEST_TOOL_NAME)),
                    span ->
                        span.hasName(CHAT + " " + TEST_CHAT_MODEL)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfying(
                                equalTo(GEN_AI_OPERATION_NAME, CHAT),
                                satisfies(
                                    GEN_AI_RESPONSE_FINISH_REASONS,
                                    reasons -> reasons.containsExactly("stop")),
                                equalTo(GEN_AI_SPAN_KIND, "LLM"),
                                equalTo(GEN_AI_USAGE_INPUT_TOKENS, 408L),
                                equalTo(GEN_AI_USAGE_OUTPUT_TOKENS, 31L),
                                equalTo(GEN_AI_USAGE_TOTAL_TOKENS, 439L))));
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
                    .toolCallbacks(getToolCallbacks())
                    .build())
            .build();

    ChatClient chatClient = getChatClient();

    List<ChatResponse> chunks =
        chatClient.prompt(prompt).stream().chatResponse().toStream().collect(Collectors.toList());

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(INVOKE_AGENT + " " + TEST_AGENT_NAME)
                            .hasAttributesSatisfying(
                                equalTo(GEN_AI_AGENT_NAME, TEST_AGENT_NAME),
                                equalTo(GEN_AI_PROVIDER_NAME, "spring-ai"),
                                equalTo(GEN_AI_OPERATION_NAME, INVOKE_AGENT),
                                equalTo(GEN_AI_REQUEST_MODEL, TEST_CHAT_MODEL),
                                equalTo(GEN_AI_RESPONSE_ID, chunks.get(0).getMetadata().getId()),
                                satisfies(
                                    GEN_AI_RESPONSE_FINISH_REASONS,
                                    reasons -> reasons.containsExactly("stop")),
                                equalTo(GEN_AI_SPAN_KIND, "AGENT"),
                                satisfies(
                                    GEN_AI_INPUT_MESSAGES, messages -> messages.contains("system")),
                                satisfies(
                                    GEN_AI_INPUT_MESSAGES,
                                    messages ->
                                        messages.contains(
                                            "You are a helpful assistant providing weather updates.")),
                                satisfies(
                                    GEN_AI_INPUT_MESSAGES, messages -> messages.contains("user")),
                                satisfies(
                                    GEN_AI_INPUT_MESSAGES,
                                    messages ->
                                        messages.contains(
                                            "What is the weather in New York City and London?")),
                                satisfies(
                                    GEN_AI_OUTPUT_MESSAGES,
                                    messages -> messages.contains("assistant")),
                                satisfies(
                                    GEN_AI_OUTPUT_MESSAGES,
                                    messages ->
                                        messages.contains(
                                            "The current weather is as follows:\\n- **New York City**: 25 degrees and sunny.\\n- **London**: 15 degrees and raining.")),
                                satisfies(
                                    GEN_AI_OUTPUT_MESSAGES, messages -> messages.contains("stop")),
                                satisfies(
                                    GEN_AI_TOOL_DEFINITIONS,
                                    messages -> messages.contains("function")),
                                satisfies(
                                    GEN_AI_TOOL_DEFINITIONS,
                                    messages -> messages.contains("get_weather")),
                                satisfies(
                                    GEN_AI_TOOL_DEFINITIONS,
                                    messages ->
                                        messages.contains(
                                            "The location to get the current temperature for"))),
                    span ->
                        span.hasName(CHAT + " " + TEST_CHAT_MODEL)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfying(
                                equalTo(GEN_AI_OPERATION_NAME, CHAT),
                                satisfies(
                                    GEN_AI_RESPONSE_FINISH_REASONS,
                                    reasons -> reasons.containsExactly("tool_calls")),
                                equalTo(GEN_AI_SPAN_KIND, "LLM")),
                    // 2 spans are compressed into 1 span
                    span ->
                        span.hasName(EXECUTE_TOOL + " " + TEST_TOOL_NAME)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfying(
                                equalTo(GEN_AI_OPERATION_NAME, EXECUTE_TOOL),
                                equalTo(GEN_AI_SPAN_KIND, "TOOL"),
                                equalTo(
                                    GEN_AI_TOOL_DESCRIPTION,
                                    "The location to get the current temperature for"),
                                equalTo(GEN_AI_TOOL_TYPE, "function"),
                                equalTo(GEN_AI_TOOL_NAME, TEST_TOOL_NAME)),
                    span ->
                        span.hasName(CHAT + " " + TEST_CHAT_MODEL)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfying(
                                equalTo(GEN_AI_OPERATION_NAME, CHAT),
                                satisfies(
                                    GEN_AI_RESPONSE_FINISH_REASONS,
                                    reasons -> reasons.containsExactly("stop")),
                                equalTo(GEN_AI_SPAN_KIND, "LLM"))));
  }
}
