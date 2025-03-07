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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.SdkResponse;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.protocols.json.SdkJsonGenerator;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;
import software.amazon.awssdk.thirdparty.jackson.core.JsonFactory;

final class BedrockRuntimeImpl {
  private BedrockRuntimeImpl() {}

  private static final AttributeKey<String> EVENT_NAME = stringKey("event.name");
  private static final AttributeKey<String> GEN_AI_SYSTEM = stringKey("gen_ai.system");

  private static final JsonFactory JSON_FACTORY = new JsonFactory();

  static boolean isBedrockRuntimeRequest(SdkRequest request) {
    if (request instanceof ConverseRequest) {
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
    }
    return null;
  }

  @Nullable
  static Long getMaxTokens(SdkRequest request) {
    if (request instanceof ConverseRequest) {
      InferenceConfiguration config = ((ConverseRequest) request).inferenceConfig();
      if (config != null) {
        return integerToLong(config.maxTokens());
      }
    }
    return null;
  }

  @Nullable
  static Double getTemperature(SdkRequest request) {
    if (request instanceof ConverseRequest) {
      InferenceConfiguration config = ((ConverseRequest) request).inferenceConfig();
      if (config != null) {
        return floatToDouble(config.temperature());
      }
    }
    return null;
  }

  @Nullable
  static Double getTopP(SdkRequest request) {
    if (request instanceof ConverseRequest) {
      InferenceConfiguration config = ((ConverseRequest) request).inferenceConfig();
      if (config != null) {
        return floatToDouble(config.topP());
      }
    }
    return null;
  }

  @Nullable
  static List<String> getStopSequences(SdkRequest request) {
    if (request instanceof ConverseRequest) {
      InferenceConfiguration config = ((ConverseRequest) request).inferenceConfig();
      if (config != null) {
        return config.stopSequences();
      }
    }
    return null;
  }

  @Nullable
  static String getStopReason(SdkResponse response) {
    if (response instanceof ConverseResponse) {
      StopReason reason = ((ConverseResponse) response).stopReason();
      if (reason != null) {
        return reason.toString();
      }
    }
    return null;
  }

  @Nullable
  static Long getUsageInputTokens(SdkResponse response) {
    if (response instanceof ConverseResponse) {
      TokenUsage usage = ((ConverseResponse) response).usage();
      if (usage != null) {
        return integerToLong(usage.inputTokens());
      }
    }
    return null;
  }

  @Nullable
  static Long getUsageOutputTokens(SdkResponse response) {
    if (response instanceof ConverseResponse) {
      TokenUsage usage = ((ConverseResponse) response).usage();
      if (usage != null) {
        return integerToLong(usage.outputTokens());
      }
    }
    return null;
  }

  static void recordRequestEvents(
      Context otelContext, Logger eventLogger, SdkRequest request, boolean captureMessageContent) {
    if (request instanceof ConverseRequest) {
      for (Message message : ((ConverseRequest) request).messages()) {
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
                  converseResponse.stopReason(),
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
      Message message, int index, @Nullable StopReason stopReason, boolean captureMessageContent) {
    StringBuilder text = null;
    List<Value<?>> toolCalls = null;
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
        toolCalls.add(convertToolCall(content.toolUse(), captureMessageContent));
      }
    }
    Map<String, Value<?>> body = new HashMap<>();
    if (text != null) {
      body.put("content", Value.of(text.toString()));
    }
    if (toolCalls != null) {
      body.put("toolCalls", Value.of(toolCalls));
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
}
