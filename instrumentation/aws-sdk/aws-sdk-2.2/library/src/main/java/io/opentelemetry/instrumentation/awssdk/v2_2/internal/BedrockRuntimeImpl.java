/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ImplicitContextKeyed;
import io.opentelemetry.context.Scope;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.SdkResponse;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.protocols.json.SdkJsonGenerator;
import software.amazon.awssdk.protocols.jsoncore.JsonNode;
import software.amazon.awssdk.protocols.jsoncore.JsonNodeParser;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDeltaEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStartEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetadataEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStartEvent;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlockStart;
import software.amazon.awssdk.thirdparty.jackson.core.JsonFactory;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class BedrockRuntimeImpl {
  private BedrockRuntimeImpl() {}

  private static final AttributeKey<String> EVENT_NAME = stringKey("event.name");
  private static final AttributeKey<String> GEN_AI_SYSTEM = stringKey("gen_ai.system");

  private static final JsonFactory JSON_FACTORY = new JsonFactory();
  private static final JsonNodeParser JSON_PARSER = JsonNode.parser();
  private static final DocumentUnmarshaller DOCUMENT_UNMARSHALLER = new DocumentUnmarshaller();

  static boolean isBedrockRuntimeRequest(SdkRequest request) {
    if (request instanceof ConverseRequest) {
      return true;
    }
    if (request instanceof ConverseStreamRequest) {
      return true;
    }
    return false;
  }

  static boolean isBedrockRuntimeResponse(SdkResponse request) {
    if (request instanceof ConverseResponse) {
      return true;
    }
    return false;
  }

  @Nullable
  static String getModelId(SdkRequest request) {
    if (request instanceof ConverseRequest) {
      return ((ConverseRequest) request).modelId();
    } else if (request instanceof ConverseStreamRequest) {
      return ((ConverseStreamRequest) request).modelId();
    }
    return null;
  }

  @Nullable
  static Long getMaxTokens(SdkRequest request) {
    InferenceConfiguration config = null;
    if (request instanceof ConverseRequest) {
      config = ((ConverseRequest) request).inferenceConfig();
    } else if (request instanceof ConverseStreamRequest) {
      config = ((ConverseStreamRequest) request).inferenceConfig();
    }
    if (config != null) {
      return integerToLong(config.maxTokens());
    }
    return null;
  }

  @Nullable
  static Double getTemperature(SdkRequest request) {
    InferenceConfiguration config = null;
    if (request instanceof ConverseRequest) {
      config = ((ConverseRequest) request).inferenceConfig();
    } else if (request instanceof ConverseStreamRequest) {
      config = ((ConverseStreamRequest) request).inferenceConfig();
    }
    if (config != null) {
      return floatToDouble(config.temperature());
    }
    return null;
  }

  @Nullable
  static Double getTopP(SdkRequest request) {
    InferenceConfiguration config = null;
    if (request instanceof ConverseRequest) {
      config = ((ConverseRequest) request).inferenceConfig();
    } else if (request instanceof ConverseStreamRequest) {
      config = ((ConverseStreamRequest) request).inferenceConfig();
    }
    if (config != null) {
      return floatToDouble(config.topP());
    }
    return null;
  }

  @Nullable
  static List<String> getStopSequences(SdkRequest request) {
    InferenceConfiguration config = null;
    if (request instanceof ConverseRequest) {
      config = ((ConverseRequest) request).inferenceConfig();
    } else if (request instanceof ConverseStreamRequest) {
      config = ((ConverseStreamRequest) request).inferenceConfig();
    }
    if (config != null) {
      return config.stopSequences();
    }
    return null;
  }

  @Nullable
  static List<String> getStopReasons(Response response) {
    SdkResponse sdkResponse = response.getSdkResponse();
    if (sdkResponse instanceof ConverseResponse) {
      StopReason reason = ((ConverseResponse) sdkResponse).stopReason();
      if (reason != null) {
        return Collections.singletonList(reason.toString());
      }
    } else {
      TracingConverseStreamResponseHandler streamHandler =
          TracingConverseStreamResponseHandler.fromContext(response.otelContext());
      if (streamHandler != null) {
        return streamHandler.stopReasons;
      }
    }
    return null;
  }

  @Nullable
  static Long getUsageInputTokens(Response response) {
    SdkResponse sdkResponse = response.getSdkResponse();
    TokenUsage usage = null;
    if (sdkResponse instanceof ConverseResponse) {
      usage = ((ConverseResponse) sdkResponse).usage();
    } else {
      TracingConverseStreamResponseHandler streamHandler =
          TracingConverseStreamResponseHandler.fromContext(response.otelContext());
      if (streamHandler != null) {
        usage = streamHandler.usage;
      }
    }
    if (usage != null) {
      return integerToLong(usage.inputTokens());
    }
    return null;
  }

  @Nullable
  static Long getUsageOutputTokens(Response response) {
    SdkResponse sdkResponse = response.getSdkResponse();
    TokenUsage usage = null;
    if (sdkResponse instanceof ConverseResponse) {
      usage = ((ConverseResponse) sdkResponse).usage();
    } else {
      TracingConverseStreamResponseHandler streamHandler =
          TracingConverseStreamResponseHandler.fromContext(response.otelContext());
      if (streamHandler != null) {
        usage = streamHandler.usage;
      }
    }
    if (usage != null) {
      return integerToLong(usage.outputTokens());
    }
    return null;
  }

  static void recordRequestEvents(
      Context otelContext, Logger eventLogger, SdkRequest request, boolean captureMessageContent) {
    if (request instanceof ConverseRequest) {
      recordRequestMessageEvents(
          otelContext, eventLogger, ((ConverseRequest) request).messages(), captureMessageContent);
    }
    if (request instanceof ConverseStreamRequest) {
      recordRequestMessageEvents(
          otelContext,
          eventLogger,
          ((ConverseStreamRequest) request).messages(),
          captureMessageContent);

      // Good a time as any to store the context for a streaming request.
      TracingConverseStreamResponseHandler.fromContext(otelContext).setOtelContext(otelContext);
    }
  }

  private static void recordRequestMessageEvents(
      Context otelContext,
      Logger eventLogger,
      List<Message> messages,
      boolean captureMessageContent) {
    for (Message message : messages) {
      long numToolResults =
          message.content().stream().filter(block -> block.toolResult() != null).count();
      if (numToolResults > 0) {
        // Tool results are different from others, emitting multiple events for a single message,
        // so treat them separately.
        emitToolResultEvents(otelContext, eventLogger, message, captureMessageContent);
        if (numToolResults == message.content().size()) {
          continue;
        }
        // There are content blocks besides tool results in the same message. While models
        // generally don't expect such usage, the SDK allows it so go ahead and generate a normal
        // message too.
      }
      LogRecordBuilder event = newEvent(otelContext, eventLogger);
      switch (message.role()) {
        case ASSISTANT:
          event.setAttribute(EVENT_NAME, "gen_ai.assistant.message");
          break;
        case USER:
          event.setAttribute(EVENT_NAME, "gen_ai.user.message");
          break;
        default:
          // unknown role, shouldn't happen in practice
          continue;
      }
      // Requests don't have index or stop reason.
      event.setBody(convertMessage(message, -1, null, captureMessageContent)).emit();
    }
  }

  static void recordResponseEvents(
      Context otelContext,
      Logger eventLogger,
      SdkResponse response,
      boolean captureMessageContent) {
    if (response instanceof ConverseResponse) {
      ConverseResponse converseResponse = (ConverseResponse) response;
      newEvent(otelContext, eventLogger)
          .setAttribute(EVENT_NAME, "gen_ai.choice")
          // Bedrock Runtime does not support multiple choices so index is always 0.
          .setBody(
              convertMessage(
                  converseResponse.output().message(),
                  0,
                  converseResponse.stopReasonAsString(),
                  captureMessageContent))
          .emit();
    }
  }

  @Nullable
  private static Long integerToLong(Integer value) {
    if (value == null) {
      return null;
    }
    return Long.valueOf(value);
  }

  @Nullable
  private static Double floatToDouble(Float value) {
    if (value == null) {
      return null;
    }
    return Double.valueOf(value);
  }

  public static BedrockRuntimeAsyncClient wrap(
      BedrockRuntimeAsyncClient asyncClient, Logger eventLogger, boolean captureMessageContent) {
    // proxy BedrockRuntimeAsyncClient so we can wrap the subscriber to converseStream to capture
    // events.
    return (BedrockRuntimeAsyncClient)
        Proxy.newProxyInstance(
            asyncClient.getClass().getClassLoader(),
            new Class<?>[] {BedrockRuntimeAsyncClient.class},
            (proxy, method, args) -> {
              if (method.getName().equals("converseStream")
                  && args.length >= 2
                  && args[1] instanceof ConverseStreamResponseHandler) {
                TracingConverseStreamResponseHandler wrapped =
                    new TracingConverseStreamResponseHandler(
                        (ConverseStreamResponseHandler) args[1],
                        eventLogger,
                        captureMessageContent);
                args[1] = wrapped;
                try (Scope ignored = wrapped.makeCurrent()) {
                  return invokeProxyMethod(method, asyncClient, args);
                }
              }
              return invokeProxyMethod(method, asyncClient, args);
            });
  }

  private static Object invokeProxyMethod(Method method, Object target, Object[] args)
      throws Throwable {
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException exception) {
      throw exception.getCause();
    }
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static class TracingConverseStreamResponseHandler
      implements ConverseStreamResponseHandler, ImplicitContextKeyed {

    @Nullable
    public static TracingConverseStreamResponseHandler fromContext(Context context) {
      return context.get(KEY);
    }

    private static final ContextKey<TracingConverseStreamResponseHandler> KEY =
        ContextKey.named("bedrock-runtime-converse-stream-response-handler");

    private final ConverseStreamResponseHandler delegate;
    private final Logger eventLogger;
    private final boolean captureMessageContent;

    private StringBuilder currentText;

    // The response handler is created and stored into context before the span, so we need to
    // also pass the later context in for recording events. While subscribers are called from a
    // single thread, it is not clear if that is guaranteed to be the same as the execution
    // interceptor so we use volatile.
    private volatile Context otelContext;

    private List<ToolUseBlock> tools;
    private ToolUseBlock.Builder currentTool;
    private StringBuilder currentToolArgs;

    List<String> stopReasons;
    TokenUsage usage;

    TracingConverseStreamResponseHandler(
        ConverseStreamResponseHandler delegate, Logger eventLogger, boolean captureMessageContent) {
      this.delegate = delegate;
      this.eventLogger = eventLogger;
      this.captureMessageContent = captureMessageContent;
    }

    @Override
    public void responseReceived(ConverseStreamResponse converseStreamResponse) {
      delegate.responseReceived(converseStreamResponse);
    }

    @Override
    public void onEventStream(SdkPublisher<ConverseStreamOutput> sdkPublisher) {
      delegate.onEventStream(
          sdkPublisher.map(
              event -> {
                handleEvent(event);
                return event;
              }));
    }

    private void handleEvent(ConverseStreamOutput event) {
      if (captureMessageContent && event instanceof MessageStartEvent) {
        if (currentText == null) {
          currentText = new StringBuilder();
        }
        currentText.setLength(0);
      }
      if (event instanceof ContentBlockStartEvent) {
        ToolUseBlockStart toolUse = ((ContentBlockStartEvent) event).start().toolUse();
        if (toolUse != null) {
          if (currentToolArgs == null) {
            currentToolArgs = new StringBuilder();
          }
          currentToolArgs.setLength(0);
          currentTool = ToolUseBlock.builder().name(toolUse.name()).toolUseId(toolUse.toolUseId());
        }
      }
      if (event instanceof ContentBlockDeltaEvent) {
        ContentBlockDelta delta = ((ContentBlockDeltaEvent) event).delta();
        if (captureMessageContent && delta.text() != null) {
          currentText.append(delta.text());
        }
        if (delta.toolUse() != null) {
          currentToolArgs.append(delta.toolUse().input());
        }
      }
      if (event instanceof ContentBlockStopEvent) {
        if (currentTool != null) {
          if (tools == null) {
            tools = new ArrayList<>();
          }
          if (currentToolArgs != null) {
            Document args = deserializeDocument(currentToolArgs.toString());
            currentTool.input(args);
          }
          tools.add(currentTool.build());
          currentTool = null;
        }
      }
      if (event instanceof MessageStopEvent) {
        if (stopReasons == null) {
          stopReasons = new ArrayList<>();
        }
        String stopReason = ((MessageStopEvent) event).stopReasonAsString();
        stopReasons.add(stopReason);
        newEvent(otelContext, eventLogger)
            .setAttribute(EVENT_NAME, "gen_ai.choice")
            .setBody(convertMessageData(currentText, tools, 0, stopReason, captureMessageContent))
            .emit();
      }
      if (event instanceof ConverseStreamMetadataEvent) {
        usage = ((ConverseStreamMetadataEvent) event).usage();
      }
    }

    @Override
    public void exceptionOccurred(Throwable throwable) {
      delegate.exceptionOccurred(throwable);
    }

    @Override
    public void complete() {
      delegate.complete();
    }

    @Override
    public Context storeInContext(Context context) {
      return context.with(KEY, this);
    }

    void setOtelContext(Context otelContext) {
      this.otelContext = otelContext;
    }
  }

  private static LogRecordBuilder newEvent(Context otelContext, Logger eventLogger) {
    return eventLogger
        .logRecordBuilder()
        .setContext(otelContext)
        .setAttribute(
            GEN_AI_SYSTEM, BedrockRuntimeAttributesGetter.GenAiSystemIncubatingValues.AWS_BEDROCK);
  }

  private static void emitToolResultEvents(
      Context otelContext, Logger eventLogger, Message message, boolean captureMessageContent) {
    for (ContentBlock content : message.content()) {
      if (content.toolResult() == null) {
        continue;
      }
      Map<String, Value<?>> body = new HashMap<>();
      body.put("id", Value.of(content.toolResult().toolUseId()));
      if (captureMessageContent) {
        StringBuilder text = new StringBuilder();
        for (ToolResultContentBlock toolContent : content.toolResult().content()) {
          if (toolContent.text() != null) {
            text.append(toolContent.text());
          }
          if (toolContent.json() != null) {
            text.append(serializeDocument(toolContent.json()));
          }
        }
        body.put("content", Value.of(text.toString()));
      }
      newEvent(otelContext, eventLogger)
          .setAttribute(EVENT_NAME, "gen_ai.tool.message")
          .setBody(Value.of(body))
          .emit();
    }
  }

  private static Value<?> convertMessage(
      Message message, int index, @Nullable String stopReason, boolean captureMessageContent) {
    StringBuilder text = null;
    List<ToolUseBlock> toolCalls = null;
    for (ContentBlock content : message.content()) {
      if (captureMessageContent && content.text() != null) {
        if (text == null) {
          text = new StringBuilder();
        }
        text.append(content.text());
      }
      if (content.toolUse() != null) {
        if (toolCalls == null) {
          toolCalls = new ArrayList<>();
        }
        toolCalls.add(content.toolUse());
      }
    }

    return convertMessageData(text, toolCalls, index, stopReason, captureMessageContent);
  }

  private static Value<?> convertMessageData(
      @Nullable StringBuilder text,
      List<ToolUseBlock> toolCalls,
      int index,
      @Nullable String stopReason,
      boolean captureMessageContent) {
    Map<String, Value<?>> body = new HashMap<>();
    if (text != null) {
      body.put("content", Value.of(text.toString()));
    }
    if (toolCalls != null) {
      List<Value<?>> toolCallValues =
          toolCalls.stream()
              .map(tool -> convertToolCall(tool, captureMessageContent))
              .collect(Collectors.toList());
      body.put("toolCalls", Value.of(toolCallValues));
    }
    if (stopReason != null) {
      body.put("finish_reason", Value.of(stopReason.toString()));
    }
    if (index >= 0) {
      body.put("index", Value.of(index));
    }
    return Value.of(body);
  }

  private static Value<?> convertToolCall(ToolUseBlock toolCall, boolean captureMessageContent) {
    Map<String, Value<?>> body = new HashMap<>();
    body.put("id", Value.of(toolCall.toolUseId()));
    body.put("name", Value.of(toolCall.name()));
    body.put("type", Value.of("function"));
    if (captureMessageContent) {
      body.put("arguments", Value.of(serializeDocument(toolCall.input())));
    }
    return Value.of(body);
  }

  private static String serializeDocument(Document document) {
    SdkJsonGenerator generator = new SdkJsonGenerator(JSON_FACTORY, "application/json");
    DocumentTypeJsonMarshaller marshaller = new DocumentTypeJsonMarshaller(generator);
    document.accept(marshaller);
    return new String(generator.getBytes(), StandardCharsets.UTF_8);
  }

  private static Document deserializeDocument(String json) {
    JsonNode node = JSON_PARSER.parse(json);
    return node.visit(DOCUMENT_UNMARSHALLER);
  }
}
