package io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0.chat.client;

import io.opentelemetry.instrumentation.api.genai.MessageCaptureOptions;
import io.opentelemetry.instrumentation.api.genai.messages.InputMessage;
import io.opentelemetry.instrumentation.api.genai.messages.InputMessages;
import io.opentelemetry.instrumentation.api.genai.messages.MessagePart;
import io.opentelemetry.instrumentation.api.genai.messages.OutputMessage;
import io.opentelemetry.instrumentation.api.genai.messages.OutputMessages;
import io.opentelemetry.instrumentation.api.genai.messages.Role;
import io.opentelemetry.instrumentation.api.genai.messages.SystemInstructions;
import io.opentelemetry.instrumentation.api.genai.messages.TextPart;
import io.opentelemetry.instrumentation.api.genai.messages.ToolCallRequestPart;
import io.opentelemetry.instrumentation.api.genai.messages.ToolCallResponsePart;
import io.opentelemetry.instrumentation.api.genai.messages.ToolDefinition;
import io.opentelemetry.instrumentation.api.genai.messages.ToolDefinitions;
import io.opentelemetry.instrumentation.api.instrumenter.genai.GenAiMessagesProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

public class ChatClientMessagesProvider implements
    GenAiMessagesProvider<ChatClientRequest, ChatClientResponse> {

  private static final String TRUNCATE_FLAG = "...[truncated]";

  private final MessageCaptureOptions messageCaptureOptions;

  ChatClientMessagesProvider(MessageCaptureOptions messageCaptureOptions) {
    this.messageCaptureOptions = messageCaptureOptions;
  }

  public static ChatClientMessagesProvider create(MessageCaptureOptions messageCaptureOptions) {
    return new ChatClientMessagesProvider(messageCaptureOptions);
  }

  @Nullable
  @Override
  public InputMessages inputMessages(ChatClientRequest request, @Nullable ChatClientResponse response) {
    if (!messageCaptureOptions.captureMessageContent()
        || request.prompt().getInstructions() == null) {
      return null;
    }

    InputMessages inputMessages = InputMessages.create();
    for (Message msg : request.prompt().getInstructions()) {
      if (msg.getMessageType() == MessageType.SYSTEM) {
        inputMessages.append(
            InputMessage.create(Role.SYSTEM, contentToMessageParts(msg.getText())));
      } else if (msg.getMessageType() == MessageType.USER) {
        inputMessages.append(InputMessage.create(Role.USER, contentToMessageParts(msg.getText())));
      } else if (msg.getMessageType() == MessageType.ASSISTANT) {
        AssistantMessage assistantMessage = (AssistantMessage) msg;
        List<MessagePart> messageParts = new ArrayList<>();

        if (assistantMessage.getText() != null && !assistantMessage.getText().isEmpty()) {
          messageParts.addAll(contentToMessageParts(assistantMessage.getText()));
        }

        if (assistantMessage.hasToolCalls()) {
          messageParts.addAll(assistantMessage
              .getToolCalls()
              .stream()
              .map(this::toolCallToMessagePart)
              .collect(Collectors.toList()));
        }
        inputMessages.append(InputMessage.create(Role.ASSISTANT, messageParts));
      } else if (msg.getMessageType() == MessageType.TOOL) {
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) msg;
        inputMessages.append(InputMessage.create(Role.TOOL, contentToMessageParts(toolResponseMessage.getResponses())));
      }
    }
    return inputMessages;
  }

  @Nullable
  @Override
  public OutputMessages outputMessages(ChatClientRequest request, @Nullable ChatClientResponse response) {
    if (!messageCaptureOptions.captureMessageContent()
        || response == null
        || response.chatResponse() == null
        || response.chatResponse().getResults() == null) {
      return null;
    }

    OutputMessages outputMessages = OutputMessages.create();
    for (Generation generation : response.chatResponse().getResults()) {
      AssistantMessage message = generation.getOutput();
      List<MessagePart> messageParts = new ArrayList<>();
      if (message != null) {
        if (message.getText() != null && !message.getText().isEmpty()) {
          messageParts.addAll(contentToMessageParts(message.getText()));
        }

        if (message.hasToolCalls()) {
          messageParts.addAll(message
              .getToolCalls()
              .stream()
              .map(this::toolCallToMessagePart)
              .collect(Collectors.toList()));
        }
      }

      outputMessages.append(
          OutputMessage.create(
              Role.ASSISTANT,
              messageParts,
              generation.getMetadata().getFinishReason().toLowerCase()));
    }
    return outputMessages;
  }

  @Nullable
  @Override
  public SystemInstructions systemInstructions(ChatClientRequest request, @Nullable ChatClientResponse response) {
    return null;
  }

  @Nullable
  @Override
  public ToolDefinitions toolDefinitions(ChatClientRequest request, @Nullable ChatClientResponse response) {
    if (request.prompt().getOptions() == null || !(request.prompt()
        .getOptions() instanceof ToolCallingChatOptions options)) {
      return null;
    }

    ToolDefinitions toolDefinitions = ToolDefinitions.create();

    // See: org.springframework.ai.model.tool.DefaultToolCallingManager.resolveToolDefinitions
    options.getToolCallbacks()
        .stream()
        .map(toolCallback -> {
          String name = toolCallback.getToolDefinition().name();
          String type = "function";
          if (messageCaptureOptions.captureMessageContent()) {
            return ToolDefinition.create(type, name, toolCallback.getToolDefinition().description(), null);
          } else {
            return ToolDefinition.create(type, name, null, null);
          }
        })
        .filter(Objects::nonNull)
        .forEach(toolDefinitions::append);

    for (String toolName : options.getToolNames()) {
      // Skip the tool if it is already present in the request toolCallbacks.
      // That might happen if a tool is defined in the options
      // both as a ToolCallback and as a tool name.
      if (options.getToolCallbacks()
          .stream()
          .anyMatch(tool -> tool.getToolDefinition().name().equals(toolName))) {
        continue;
      }
      toolDefinitions.append(ToolDefinition.create("function", toolName, null, null));
    }

    return toolDefinitions;
  }

  private List<MessagePart> contentToMessageParts(String content) {
    return Collections.singletonList(TextPart.create(truncateTextContent(content)));
  }

  private MessagePart toolCallToMessagePart(ToolCall call) {
    if (call != null) {
      return ToolCallRequestPart.create(call.id(), call.name(), call.arguments());
    }
    return ToolCallRequestPart.create("unknown_function");
  }

  private List<MessagePart> contentToMessageParts(List<ToolResponse> toolResponses) {
    if (toolResponses == null) {
      return Collections.singletonList(ToolCallResponsePart.create(""));
    }

    return toolResponses.stream()
        .map(response ->
            ToolCallResponsePart.create(
                response.id(), truncateTextContent(response.responseData())))
        .collect(Collectors.toList());
  }

  private String truncateTextContent(String content) {
    if (!content.endsWith(TRUNCATE_FLAG) && content.length() > messageCaptureOptions.maxMessageContentLength()) {
      content = content.substring(0, messageCaptureOptions.maxMessageContentLength()) + TRUNCATE_FLAG;
    }
    return content;
  }
}
