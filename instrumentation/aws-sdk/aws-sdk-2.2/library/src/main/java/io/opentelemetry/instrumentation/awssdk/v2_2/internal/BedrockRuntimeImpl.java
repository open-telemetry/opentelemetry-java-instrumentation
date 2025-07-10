/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.TracingExecutionInterceptor.SDK_REQUEST_ATTRIBUTE;

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
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import software.amazon.awssdk.awscore.eventstream.EventStreamResponseHandler;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.SdkResponse;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.core.interceptor.ExecutionAttribute;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.protocols.json.SdkJsonGenerator;
import software.amazon.awssdk.protocols.jsoncore.JsonNode;
import software.amazon.awssdk.protocols.jsoncore.JsonNodeParser;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDeltaEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStartEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetadataEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStartEvent;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.PayloadPart;
import software.amazon.awssdk.services.bedrockruntime.model.ResponseStream;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock;
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

  // copied from GenAiIncubatingAttributes
  private static final class GenAiOperationNameIncubatingValues {
    static final String CHAT = "chat";
    static final String TEXT_COMPLETION = "text_completion";

    private GenAiOperationNameIncubatingValues() {}
  }

  private static final AttributeKey<String> EVENT_NAME = stringKey("event.name");
  private static final AttributeKey<String> GEN_AI_SYSTEM = stringKey("gen_ai.system");

  private static final ExecutionAttribute<Document> INVOKE_MODEL_REQUEST_BODY =
      new ExecutionAttribute<>(BedrockRuntimeImpl.class.getName() + ".InvokeModelRequestBody");

  private static final ExecutionAttribute<Document> INVOKE_MODEL_RESPONSE_BODY =
      new ExecutionAttribute<>(BedrockRuntimeImpl.class.getName() + ".InvokeModelResponseBody");

  private static final JsonFactory JSON_FACTORY = new JsonFactory();
  private static final JsonNodeParser JSON_PARSER = JsonNode.parser();
  private static final DocumentUnmarshaller DOCUMENT_UNMARSHALLER = new DocumentUnmarshaller();

  // used to approximate input/output token count for Cohere and Mistral AI models,
  // which don't provide these values in the response body.
  // https://docs.aws.amazon.com/bedrock/latest/userguide/model-customization-prepare.html
  private static final Double CHARS_PER_TOKEN = 6.0;

  private enum ModelFamily {
    AMAZON_NOVA,
    ANTHROPIC_CLAUDE
  }

  static boolean isBedrockRuntimeRequest(SdkRequest request) {
    if (request instanceof ConverseRequest) {
      return true;
    }
    if (request instanceof ConverseStreamRequest) {
      return true;
    }
    if (request instanceof InvokeModelRequest) {
      return true;
    }
    if (request instanceof InvokeModelWithResponseStreamRequest) {
      return true;
    }
    return false;
  }

  static boolean isBedrockRuntimeResponse(SdkResponse request) {
    if (request instanceof ConverseResponse) {
      return true;
    }
    if (request instanceof InvokeModelResponse) {
      return true;
    }
    return false;
  }

  static void maybeParseInvokeModelRequest(
      ExecutionAttributes executionAttributes, SdkRequest request) {
    SdkBytes payload = null;
    if (request instanceof InvokeModelRequest) {
      payload = ((InvokeModelRequest) request).body();
    }
    if (request instanceof InvokeModelWithResponseStreamRequest) {
      payload = ((InvokeModelWithResponseStreamRequest) request).body();
    }
    if (payload != null) {
      Document body = deserializeDocument(payload.asByteArrayUnsafe());
      executionAttributes.putAttribute(INVOKE_MODEL_REQUEST_BODY, body);
    }
  }

  static void maybeParseInvokeModelResponse(
      ExecutionAttributes executionAttributes, SdkResponse response) {
    if (response instanceof InvokeModelResponse) {
      Document body =
          deserializeDocument(((InvokeModelResponse) response).body().asByteArrayUnsafe());
      executionAttributes.putAttribute(INVOKE_MODEL_RESPONSE_BODY, body);
    }
  }

  @Nullable
  static String getModelId(ExecutionAttributes executionAttributes) {
    SdkRequest request = executionAttributes.getAttribute(SDK_REQUEST_ATTRIBUTE);
    if (request instanceof ConverseRequest) {
      return ((ConverseRequest) request).modelId();
    }
    if (request instanceof ConverseStreamRequest) {
      return ((ConverseStreamRequest) request).modelId();
    }
    if (request instanceof InvokeModelRequest) {
      return ((InvokeModelRequest) request).modelId();
    }
    if (request instanceof InvokeModelWithResponseStreamRequest) {
      return ((InvokeModelWithResponseStreamRequest) request).modelId();
    }
    return null;
  }

  @Nullable
  static String getOperationName(ExecutionAttributes executionAttributes) {
    SdkRequest request = executionAttributes.getAttribute(SDK_REQUEST_ATTRIBUTE);
    if (request instanceof ConverseRequest) {
      return GenAiOperationNameIncubatingValues.CHAT;
    }
    if (request instanceof ConverseStreamRequest) {
      return GenAiOperationNameIncubatingValues.CHAT;
    }
    if (request instanceof InvokeModelRequest) {
      return getOperationNameInvokeModel(((InvokeModelRequest) request).modelId());
    }
    if (request instanceof InvokeModelWithResponseStreamRequest) {
      return getOperationNameInvokeModel(
          ((InvokeModelWithResponseStreamRequest) request).modelId());
    }

    return null;
  }

  @Nullable
  private static String getOperationNameInvokeModel(@Nullable String modelId) {
    if (modelId == null) {
      return null;
    }
    if (modelId.startsWith("amazon.titan")) {
      // titan using invoke model is a text completion request
      return GenAiOperationNameIncubatingValues.TEXT_COMPLETION;
    }
    return GenAiOperationNameIncubatingValues.CHAT;
  }

  @Nullable
  static Long getMaxTokens(ExecutionAttributes executionAttributes) {
    SdkRequest request = executionAttributes.getAttribute(SDK_REQUEST_ATTRIBUTE);
    if (request instanceof InvokeModelRequest) {
      return getMaxTokensInvokeModel(executionAttributes, ((InvokeModelRequest) request).modelId());
    }
    if (request instanceof InvokeModelWithResponseStreamRequest) {
      return getMaxTokensInvokeModel(
          executionAttributes, ((InvokeModelWithResponseStreamRequest) request).modelId());
    }

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
  private static Long getMaxTokensInvokeModel(
      ExecutionAttributes executionAttributes, @Nullable String modelId) {
    if (modelId == null) {
      return null;
    }
    Document body = executionAttributes.getAttribute(INVOKE_MODEL_REQUEST_BODY);
    if (!body.isMap()) {
      return null;
    }
    Document count = null;
    if (modelId.startsWith("amazon.titan")) {
      Document config = body.asMap().get("textGenerationConfig");
      if (config == null || !config.isMap()) {
        return null;
      }
      count = config.asMap().get("maxTokenCount");
    } else if (modelId.startsWith("amazon.nova")) {
      Document config = body.asMap().get("inferenceConfig");
      if (config == null || !config.isMap()) {
        return null;
      }
      count = config.asMap().get("max_new_tokens");
    } else if (modelId.startsWith("anthropic.claude")
        || modelId.startsWith("cohere.command")
        || modelId.startsWith("mistral.mistral")) {
      count = body.asMap().get("max_tokens");
    } else if (modelId.startsWith("meta.llama")) {
      count = body.asMap().get("max_gen_len");
    }
    if (count != null && count.isNumber()) {
      return count.asNumber().longValue();
    }
    return null;
  }

  @Nullable
  static Double getTemperature(ExecutionAttributes executionAttributes) {
    SdkRequest request = executionAttributes.getAttribute(SDK_REQUEST_ATTRIBUTE);
    if (request instanceof InvokeModelRequest) {
      return getTemperatureInvokeModel(
          executionAttributes, ((InvokeModelRequest) request).modelId());
    }
    if (request instanceof InvokeModelWithResponseStreamRequest) {
      return getTemperatureInvokeModel(
          executionAttributes, ((InvokeModelWithResponseStreamRequest) request).modelId());
    }

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
  private static Double getTemperatureInvokeModel(
      ExecutionAttributes executionAttributes, @Nullable String modelId) {
    if (modelId == null) {
      return null;
    }
    Document body = executionAttributes.getAttribute(INVOKE_MODEL_REQUEST_BODY);
    if (!body.isMap()) {
      return null;
    }
    Document temperature = null;
    if (modelId.startsWith("amazon.titan")) {
      Document config = body.asMap().get("textGenerationConfig");
      if (config == null || !config.isMap()) {
        return null;
      }
      temperature = config.asMap().get("temperature");
    } else if (modelId.startsWith("amazon.nova")) {
      Document config = body.asMap().get("inferenceConfig");
      if (config == null || !config.isMap()) {
        return null;
      }
      temperature = config.asMap().get("temperature");
    } else if (modelId.startsWith("anthropic.claude")
        || modelId.startsWith("meta.llama")
        || modelId.startsWith("cohere.command")
        || modelId.startsWith("mistral.mistral")) {
      temperature = body.asMap().get("temperature");
    }
    if (temperature != null && temperature.isNumber()) {
      return temperature.asNumber().doubleValue();
    }
    return null;
  }

  @Nullable
  static Double getTopP(ExecutionAttributes executionAttributes) {
    SdkRequest request = executionAttributes.getAttribute(SDK_REQUEST_ATTRIBUTE);
    if (request instanceof InvokeModelRequest) {
      return getToppInvokeModel(executionAttributes, ((InvokeModelRequest) request).modelId());
    }
    if (request instanceof InvokeModelWithResponseStreamRequest) {
      return getToppInvokeModel(
          executionAttributes, ((InvokeModelWithResponseStreamRequest) request).modelId());
    }

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

  private static Double getToppInvokeModel(
      ExecutionAttributes executionAttributes, @Nullable String modelId) {
    if (modelId == null) {
      return null;
    }
    Document body = executionAttributes.getAttribute(INVOKE_MODEL_REQUEST_BODY);
    if (!body.isMap()) {
      return null;
    }
    Document topP = null;
    if (modelId.startsWith("amazon.titan")) {
      Document config = body.asMap().get("textGenerationConfig");
      if (config == null || !config.isMap()) {
        return null;
      }
      topP = config.asMap().get("topP");
    } else if (modelId.startsWith("amazon.nova")) {
      Document config = body.asMap().get("inferenceConfig");
      if (config == null || !config.isMap()) {
        return null;
      }
      topP = config.asMap().get("topP");
    } else if (modelId.startsWith("anthropic.claude")
        || modelId.startsWith("meta.llama")
        || modelId.startsWith("mistral.mistral")) {
      topP = body.asMap().get("top_p");
    } else if (modelId.startsWith("cohere.command")) {
      topP = body.asMap().get("p");
    }
    if (topP != null && topP.isNumber()) {
      return topP.asNumber().doubleValue();
    }
    return null;
  }

  @Nullable
  static List<String> getStopSequences(ExecutionAttributes executionAttributes) {
    SdkRequest request = executionAttributes.getAttribute(SDK_REQUEST_ATTRIBUTE);
    if (request instanceof InvokeModelRequest) {
      return getStopSequences(executionAttributes, ((InvokeModelRequest) request).modelId());
    }
    if (request instanceof InvokeModelWithResponseStreamRequest) {
      return getStopSequences(
          executionAttributes, ((InvokeModelWithResponseStreamRequest) request).modelId());
    }

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
  private static List<String> getStopSequences(
      ExecutionAttributes executionAttributes, @Nullable String modelId) {
    if (modelId == null) {
      return null;
    }
    Document body = executionAttributes.getAttribute(INVOKE_MODEL_REQUEST_BODY);
    if (!body.isMap()) {
      return null;
    }
    Document stopSequences = null;
    if (modelId.startsWith("amazon.titan")) {
      Document config = body.asMap().get("textGenerationConfig");
      if (config == null || !config.isMap()) {
        return null;
      }
      stopSequences = config.asMap().get("stopSequences");
    } else if (modelId.startsWith("amazon.nova")) {
      Document config = body.asMap().get("inferenceConfig");
      if (config == null || !config.isMap()) {
        return null;
      }
      stopSequences = config.asMap().get("stopSequences");
    } else if (modelId.startsWith("anthropic.claude") || modelId.startsWith("cohere.command")) {
      stopSequences = body.asMap().get("stop_sequences");
    } else if (modelId.startsWith("mistral.mistral")) {
      stopSequences = body.asMap().get("stop");
    }
    // meta llama request does not support stop sequences
    if (stopSequences != null && stopSequences.isList()) {
      return stopSequences.asList().stream()
          .filter(Document::isString)
          .map(Document::asString)
          .collect(Collectors.toList());
    }
    return null;
  }

  @Nullable
  static List<String> getStopReasons(ExecutionAttributes executionAttributes, Response response) {
    SdkResponse sdkResponse = response.getSdkResponse();
    if (sdkResponse instanceof InvokeModelResponse) {
      return getStopReasons(
          executionAttributes,
          (InvokeModelRequest) executionAttributes.getAttribute(SDK_REQUEST_ATTRIBUTE));
    }

    if (sdkResponse instanceof ConverseResponse) {
      StopReason reason = ((ConverseResponse) sdkResponse).stopReason();
      if (reason != null) {
        return Collections.singletonList(reason.toString());
      }
    } else {
      BedrockRuntimeStreamResponseHandler<?, ?> streamHandler =
          BedrockRuntimeStreamResponseHandler.fromContext(response.otelContext());
      if (streamHandler != null) {
        return streamHandler.stopReasons;
      }
    }
    return null;
  }

  @Nullable
  private static List<String> getStopReasons(
      ExecutionAttributes executionAttributes, InvokeModelRequest request) {
    String modelId = request.modelId();
    if (modelId == null) {
      return null;
    }
    Document body = executionAttributes.getAttribute(INVOKE_MODEL_RESPONSE_BODY);
    if (!body.isMap()) {
      return null;
    }
    if (modelId.startsWith("amazon.titan")) {
      List<String> stopReasons = new ArrayList<>();
      Document results = body.asMap().get("results");
      if (results == null || !results.isList()) {
        return null;
      }
      for (Document result : results.asList()) {
        Document completionReason = result.asMap().get("completionReason");
        if (completionReason == null || !completionReason.isString()) {
          continue;
        }
        stopReasons.add(completionReason.asString());
      }
      return stopReasons;
    }
    Document stopReason = null;
    if (modelId.startsWith("amazon.nova")) {
      stopReason = body.asMap().get("stopReason");
    } else if (modelId.startsWith("anthropic.claude") || modelId.startsWith("meta.llama")) {
      stopReason = body.asMap().get("stop_reason");
    } else if (modelId.startsWith("cohere.command-r")) {
      stopReason = body.asMap().get("finish_reason");
    } else if (modelId.startsWith("cohere.command")) {
      List<String> stopReasons = new ArrayList<>();
      Document results = body.asMap().get("generations");
      if (results == null || !results.isList()) {
        return null;
      }
      for (Document result : results.asList()) {
        stopReason = result.asMap().get("finish_reason");
        if (stopReason == null || !stopReason.isString()) {
          continue;
        }
        stopReasons.add(stopReason.asString());
      }
      return stopReasons;
    } else if (modelId.startsWith("mistral.mistral")) {
      List<String> stopReasons = new ArrayList<>();
      Document results = body.asMap().get("outputs");
      if (results == null || !results.isList()) {
        return null;
      }
      for (Document result : results.asList()) {
        stopReason = result.asMap().get("stop_reason");
        if (stopReason == null || !stopReason.isString()) {
          continue;
        }
        stopReasons.add(stopReason.asString());
      }
      return stopReasons;
    }
    if (stopReason != null && stopReason.isString()) {
      return Collections.singletonList(stopReason.asString());
    }
    return null;
  }

  @Nullable
  static Long getUsageInputTokens(ExecutionAttributes executionAttributes, Response response) {
    SdkResponse sdkResponse = response.getSdkResponse();
    if (sdkResponse instanceof InvokeModelResponse) {
      return getUsageInputTokens(
          executionAttributes,
          (InvokeModelRequest) executionAttributes.getAttribute(SDK_REQUEST_ATTRIBUTE));
    }

    TokenUsage usage = null;
    if (sdkResponse instanceof ConverseResponse) {
      usage = ((ConverseResponse) sdkResponse).usage();
    } else {
      BedrockRuntimeStreamResponseHandler<?, ?> streamHandler =
          BedrockRuntimeStreamResponseHandler.fromContext(response.otelContext());
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
  private static Long getUsageInputTokens(
      ExecutionAttributes executionAttributes, InvokeModelRequest request) {
    String modelId = request.modelId();
    if (modelId == null) {
      return null;
    }
    Document body = executionAttributes.getAttribute(INVOKE_MODEL_RESPONSE_BODY);
    if (!body.isMap()) {
      return null;
    }
    Document count = null;
    if (modelId.startsWith("amazon.titan")) {
      count = body.asMap().get("inputTextTokenCount");
    } else if (modelId.startsWith("amazon.nova")) {
      Document usage = body.asMap().get("usage");
      if (usage == null || !usage.isMap()) {
        return null;
      }
      count = usage.asMap().get("inputTokens");
    } else if (modelId.startsWith("anthropic.claude")) {
      Document usage = body.asMap().get("usage");
      if (usage == null || !usage.isMap()) {
        return null;
      }
      count = usage.asMap().get("input_tokens");
    } else if (modelId.startsWith("meta.llama")) {
      count = body.asMap().get("prompt_token_count");
    } else if (modelId.startsWith("cohere.command-r")) {
      // approximate input tokens based on prompt length
      Document requestBody = executionAttributes.getAttribute(INVOKE_MODEL_REQUEST_BODY);
      if (requestBody == null || !requestBody.isMap()) {
        return null;
      }
      String prompt = requestBody.asMap().get("message").asString();
      if (prompt == null) {
        return null;
      }
      count = Document.fromNumber(Math.ceil(prompt.length() / CHARS_PER_TOKEN));
    } else if (modelId.startsWith("cohere.command") || modelId.startsWith("mistral.mistral")) {
      // approximate input tokens based on prompt length
      Document requestBody = executionAttributes.getAttribute(INVOKE_MODEL_REQUEST_BODY);
      if (requestBody == null || !requestBody.isMap()) {
        return null;
      }
      String prompt = requestBody.asMap().get("prompt").asString();
      if (prompt == null) {
        return null;
      }
      count = Document.fromNumber(Math.ceil(prompt.length() / CHARS_PER_TOKEN));
    }
    if (count != null && count.isNumber()) {
      return count.asNumber().longValue();
    }
    return null;
  }

  @Nullable
  static Long getUsageOutputTokens(ExecutionAttributes executionAttributes, Response response) {
    SdkResponse sdkResponse = response.getSdkResponse();
    if (sdkResponse instanceof InvokeModelResponse) {
      return getUsageOutputTokens(
          executionAttributes,
          (InvokeModelRequest) executionAttributes.getAttribute(SDK_REQUEST_ATTRIBUTE));
    }

    TokenUsage usage = null;
    if (sdkResponse instanceof ConverseResponse) {
      usage = ((ConverseResponse) sdkResponse).usage();
    } else {
      BedrockRuntimeStreamResponseHandler<?, ?> streamHandler =
          BedrockRuntimeStreamResponseHandler.fromContext(response.otelContext());
      if (streamHandler != null) {
        usage = streamHandler.usage;
      }
    }
    if (usage != null) {
      return integerToLong(usage.outputTokens());
    }
    return null;
  }

  @Nullable
  private static Long getUsageOutputTokens(
      ExecutionAttributes executionAttributes, InvokeModelRequest request) {
    String modelId = request.modelId();
    if (modelId == null) {
      return null;
    }
    Document body = executionAttributes.getAttribute(INVOKE_MODEL_RESPONSE_BODY);
    if (!body.isMap()) {
      return null;
    }
    Document count = null;
    if (modelId.startsWith("amazon.titan")) {
      Document results = body.asMap().get("results");
      if (results == null || !results.isList()) {
        return null;
      }
      long outputTokens = 0;
      for (Document result : results.asList()) {
        Document tokenCount = result.asMap().get("tokenCount");
        if (tokenCount == null || !tokenCount.isNumber()) {
          continue;
        }
        outputTokens += tokenCount.asNumber().intValue();
      }
      return outputTokens;
    } else if (modelId.startsWith("amazon.nova")) {
      Document usage = body.asMap().get("usage");
      if (usage == null || !usage.isMap()) {
        return null;
      }
      count = usage.asMap().get("outputTokens");
    } else if (modelId.startsWith("anthropic.claude")) {
      Document usage = body.asMap().get("usage");
      if (usage == null || !usage.isMap()) {
        return null;
      }
      count = usage.asMap().get("output_tokens");
    } else if (modelId.startsWith("meta.llama")) {
      count = body.asMap().get("generation_token_count");
    } else if (modelId.startsWith("cohere.command-r")) {
      Document text = body.asMap().get("text");
      if (text == null || !text.isString()) {
        return null;
      }
      count = Document.fromNumber(Math.ceil(text.asString().length() / CHARS_PER_TOKEN));
    } else if (modelId.startsWith("cohere.command")) {
      Document generations = body.asMap().get("generations");
      if (generations == null || !generations.isList()) {
        return null;
      }
      long outputLength = 0;
      for (Document generation : generations.asList()) {
        Document text = generation.asMap().get("text");
        if (text == null || !text.isString()) {
          continue;
        }
        outputLength += text.asString().length();
      }
      count = Document.fromNumber(Math.ceil(outputLength / CHARS_PER_TOKEN));
    } else if (modelId.startsWith("mistral.mistral")) {
      Document outputs = body.asMap().get("outputs");
      if (outputs == null || !outputs.isList()) {
        return null;
      }
      long outputLength = 0;
      for (Document output : outputs.asList()) {
        Document text = output.asMap().get("text");
        if (text == null || !text.isString()) {
          continue;
        }
        outputLength += text.asString().length();
      }
      count = Document.fromNumber(Math.ceil(outputLength / CHARS_PER_TOKEN));
    }
    if (count != null && count.isNumber()) {
      return count.asNumber().longValue();
    }
    return null;
  }

  static void recordRequestEvents(
      Context otelContext,
      Logger eventLogger,
      ExecutionAttributes executionAttributes,
      SdkRequest request,
      boolean captureMessageContent) {
    // Good a time as any to store the context for a streaming request.
    BedrockRuntimeStreamResponseHandler<?, ?> streamHandler =
        BedrockRuntimeStreamResponseHandler.fromContext(otelContext);
    if (streamHandler != null) {
      streamHandler.setOtelContext(otelContext);
    }

    if (request instanceof InvokeModelRequest) {
      Document body = executionAttributes.getAttribute(INVOKE_MODEL_REQUEST_BODY);
      recordInvokeModelRequestEvents(
          otelContext,
          eventLogger,
          ((InvokeModelRequest) request).modelId(),
          body,
          captureMessageContent);
      return;
    }
    if (request instanceof InvokeModelWithResponseStreamRequest) {
      Document body = executionAttributes.getAttribute(INVOKE_MODEL_REQUEST_BODY);
      recordInvokeModelRequestEvents(
          otelContext,
          eventLogger,
          ((InvokeModelWithResponseStreamRequest) request).modelId(),
          body,
          captureMessageContent);
      return;
    }
    if (request instanceof ConverseRequest) {
      recordRequestMessageEvents(
          otelContext, eventLogger, ((ConverseRequest) request).messages(), captureMessageContent);
      return;
    }
    if (request instanceof ConverseStreamRequest) {
      recordRequestMessageEvents(
          otelContext,
          eventLogger,
          ((ConverseStreamRequest) request).messages(),
          captureMessageContent);
    }
  }

  private static void recordInvokeModelRequestEvents(
      Context otelContext,
      Logger eventLogger,
      @Nullable String modelId,
      Document body,
      boolean captureMessageContent) {
    if (!body.isMap()) {
      return;
    }
    if (modelId == null) {
      return;
    }

    if (modelId.startsWith("amazon.titan")) {
      Document inputText = body.asMap().get("inputText");
      if (inputText == null || !inputText.isString()) {
        return;
      }
      Message message =
          Message.builder()
              .role(ConversationRole.USER)
              .content(ContentBlock.fromText(inputText.asString()))
              .build();
      recordRequestMessageEvents(
          otelContext, eventLogger, Collections.singletonList(message), captureMessageContent);
      return;
    }

    ModelFamily modelFamily = null;
    if (modelId.startsWith("amazon.nova")) {
      modelFamily = ModelFamily.AMAZON_NOVA;
    } else if (modelId.startsWith("anthropic.claude")) {
      modelFamily = ModelFamily.ANTHROPIC_CLAUDE;
    }
    if (modelFamily == ModelFamily.AMAZON_NOVA || modelFamily == ModelFamily.ANTHROPIC_CLAUDE) {
      Document messages = body.asMap().get("messages");
      if (messages == null || !messages.isList()) {
        return;
      }
      List<Message> parsedMessages = new ArrayList<>();
      for (Document message : messages.asList()) {
        if (!message.isMap()) {
          continue;
        }
        Document role = message.asMap().get("role");
        if (role == null || !role.isString()) {
          continue;
        }
        Document content = message.asMap().get("content");
        if (content == null || !content.isList()) {
          continue;
        }
        List<ContentBlock> parsedContentBlocks = new ArrayList<>();
        for (Document contentBlock : content.asList()) {
          ContentBlock parsed = parseModelContentBlock(modelFamily, contentBlock);
          if (parsed != null) {
            parsedContentBlocks.add(parsed);
          }
        }
        parsedMessages.add(
            Message.builder().role(role.asString()).content(parsedContentBlocks).build());
      }
      recordRequestMessageEvents(otelContext, eventLogger, parsedMessages, captureMessageContent);
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
      ExecutionAttributes executionAttributes,
      SdkResponse response,
      boolean captureMessageContent) {
    if (response instanceof InvokeModelResponse) {
      InvokeModelRequest request =
          (InvokeModelRequest) executionAttributes.getAttribute(SDK_REQUEST_ATTRIBUTE);
      Document body = executionAttributes.getAttribute(INVOKE_MODEL_RESPONSE_BODY);
      recordInvokeModelResponseEvents(
          otelContext, eventLogger, request, body, captureMessageContent);
    }
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

  private static void recordInvokeModelResponseEvents(
      Context otelContext,
      Logger eventLogger,
      InvokeModelRequest request,
      Document body,
      boolean captureMessageContent) {
    if (!body.isMap()) {
      return;
    }
    String modelId = request.modelId();
    if (modelId == null) {
      return;
    }
    ModelFamily modelFamily = null;
    if (modelId.startsWith("amazon.titan")) {
      // Text completion records an event per result.
      Document results = body.asMap().get("results");
      if (results == null || !results.isList()) {
        return;
      }
      int index = 0;
      for (Document result : results.asList()) {
        Document completionReason = result.asMap().get("completionReason");
        if (completionReason == null || !completionReason.isString()) {
          continue;
        }

        Message.Builder parsedMessage = Message.builder().role(ConversationRole.ASSISTANT);

        Document outputText = result.asMap().get("outputText");
        if (outputText != null && outputText.isString()) {
          parsedMessage.content(ContentBlock.fromText(outputText.asString()));
        }
        newEvent(otelContext, eventLogger)
            .setAttribute(EVENT_NAME, "gen_ai.choice")
            .setBody(
                convertMessage(
                    parsedMessage.build(),
                    index,
                    completionReason.asString(),
                    captureMessageContent))
            .emit();
        index++;
      }
      return;
    }

    String stopReasonString = null;
    Document content = null;
    if (modelId.startsWith("amazon.nova")) {
      modelFamily = ModelFamily.AMAZON_NOVA;
      Document stopReason = body.asMap().get("stopReason");
      Document output = body.asMap().get("output");
      if (output == null || !output.isMap()) {
        return;
      }
      Document message = output.asMap().get("message");
      if (message == null || !message.isMap()) {
        return;
      }
      content = message.asMap().get("content");
      stopReasonString = stopReason.asString();
    } else if (modelId.startsWith("anthropic.claude")) {
      modelFamily = ModelFamily.ANTHROPIC_CLAUDE;
      Document stopReason = body.asMap().get("stop_reason");
      content = body.asMap().get("content");
      stopReasonString = stopReason.asString();
    }

    if (content == null || !content.isList()) {
      return;
    }
    List<ContentBlock> parsedContentBlocks = new ArrayList<>();
    for (Document contentBlock : content.asList()) {
      ContentBlock parsed = parseModelContentBlock(modelFamily, contentBlock);
      if (parsed != null) {
        parsedContentBlocks.add(parsed);
      }
    }
    Message parsedMessage =
        Message.builder().role(ConversationRole.ASSISTANT).content(parsedContentBlocks).build();
    newEvent(otelContext, eventLogger)
        .setAttribute(EVENT_NAME, "gen_ai.choice")
        .setBody(convertMessage(parsedMessage, 0, stopReasonString, captureMessageContent))
        .emit();
  }

  @Nullable
  private static ContentBlock parseModelContentBlock(
      ModelFamily modelFamily, Document contentBlock) {
    switch (modelFamily) {
      case AMAZON_NOVA:
        return parseAmazonNovaContentBlock(contentBlock);
      case ANTHROPIC_CLAUDE:
        return parseAnthropicClaudeContentBlock(contentBlock);
    }
    return null;
  }

  @Nullable
  private static ContentBlock parseAmazonNovaContentBlock(Document contentBlock) {
    Document text = contentBlock.asMap().get("text");
    if (text != null && text.isString()) {
      return ContentBlock.fromText(text.asString());
    }

    Document toolUse = contentBlock.asMap().get("toolUse");
    if (toolUse != null && toolUse.isMap()) {
      ToolUseBlock.Builder toolUseBlock = ToolUseBlock.builder();
      handleToolUseAmazonNova(toolUse, toolUseBlock);
      return ContentBlock.fromToolUse(toolUseBlock.build());
    }

    Document toolResult = contentBlock.asMap().get("toolResult");
    if (toolResult != null && toolResult.isMap()) {
      Document toolUseId = toolResult.asMap().get("toolUseId");
      if (toolUseId != null && toolUseId.isString()) {
        ToolResultBlock.Builder resultBlockBuilder =
            ToolResultBlock.builder().toolUseId(toolUseId.asString());
        Document toolResultContent = toolResult.asMap().get("content");
        if (toolResultContent != null && toolResultContent.isList()) {
          List<ToolResultContentBlock> toolResultContentBlocks =
              toolResultContent.asList().stream()
                  .map(
                      toolResultContentBlockDoc -> {
                        if (toolResultContentBlockDoc.isMap()) {
                          Document json = toolResultContentBlockDoc.asMap().get("json");
                          if (json != null) {
                            return ToolResultContentBlock.builder().json(json).build();
                          }
                        }
                        return null;
                      })
                  .filter(Objects::nonNull)
                  .collect(Collectors.toList());
          resultBlockBuilder.content(toolResultContentBlocks);
        }
        return ContentBlock.fromToolResult(resultBlockBuilder.build());
      }
    }

    return null;
  }

  @Nullable
  private static ContentBlock parseAnthropicClaudeContentBlock(Document contentBlock) {
    Document type = contentBlock.asMap().get("type");
    if (type == null || !type.isString()) {
      return null;
    }

    switch (type.asString()) {
      case "text":
        {
          Document text = contentBlock.asMap().get("text");
          if (text != null && text.isString()) {
            return ContentBlock.fromText(text.asString());
          }
          return null;
        }
      case "tool_use":
        {
          ToolUseBlock.Builder toolUseBlock = ToolUseBlock.builder();
          handleToolUseAnthropicCloud(contentBlock, toolUseBlock);
          return ContentBlock.fromToolUse(toolUseBlock.build());
        }
      case "tool_result":
        {
          Document toolUseId = contentBlock.asMap().get("tool_use_id");
          if (toolUseId != null && toolUseId.isString()) {
            ToolResultBlock.Builder resultBlockBuilder =
                ToolResultBlock.builder().toolUseId(toolUseId.asString());
            Document toolResultContent = contentBlock.asMap().get("content");
            if (toolResultContent != null) {
              resultBlockBuilder.content(ToolResultContentBlock.fromJson(toolResultContent));
            }
            return ContentBlock.fromToolResult(resultBlockBuilder.build());
          }
          return null;
        }
      default:
        // pass through
    }

    return null;
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
              } else if (method.getName().equals("invokeModelWithResponseStream")
                  && args.length >= 2
                  && args[0] instanceof InvokeModelWithResponseStreamRequest
                  && args[1] instanceof InvokeModelWithResponseStreamResponseHandler) {
                InvokeModelWithResponseStreamRequest request =
                    (InvokeModelWithResponseStreamRequest) args[0];
                TracingInvokeModelWithResponseStreamResponseHandler wrapped =
                    new TracingInvokeModelWithResponseStreamResponseHandler(
                        (InvokeModelWithResponseStreamResponseHandler) args[1],
                        eventLogger,
                        captureMessageContent,
                        request.modelId());
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

  abstract static class BedrockRuntimeStreamResponseHandler<R, S>
      implements EventStreamResponseHandler<R, S>, ImplicitContextKeyed {
    @Nullable
    public static BedrockRuntimeStreamResponseHandler<?, ?> fromContext(Context context) {
      return context.get(KEY);
    }

    private static final ContextKey<BedrockRuntimeStreamResponseHandler<?, ?>> KEY =
        ContextKey.named("bedrock-runtime-stream-response-handler");

    private final EventStreamResponseHandler<R, S> delegate;

    // The response handler is created and stored into context before the span, so we need to
    // also pass the later context in for recording events. While subscribers are called from a
    // single thread, it is not clear if that is guaranteed to be the same as the execution
    // interceptor so we use volatile.
    volatile Context otelContext;

    List<String> stopReasons;
    TokenUsage usage;

    BedrockRuntimeStreamResponseHandler(EventStreamResponseHandler<R, S> delegate) {
      this.delegate = delegate;
    }

    protected abstract void handleEvent(S event);

    @Override
    public final void responseReceived(R response) {
      delegate.responseReceived(response);
    }

    @Override
    public final void onEventStream(SdkPublisher<S> sdkPublisher) {
      delegate.onEventStream(
          sdkPublisher.map(
              event -> {
                handleEvent(event);
                return event;
              }));
    }

    @Override
    public final void exceptionOccurred(Throwable throwable) {
      delegate.exceptionOccurred(throwable);
    }

    @Override
    public final void complete() {
      delegate.complete();
    }

    @Override
    public final Context storeInContext(Context context) {
      return context.with(KEY, this);
    }

    final void setOtelContext(Context otelContext) {
      this.otelContext = otelContext;
    }
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static class TracingConverseStreamResponseHandler
      extends BedrockRuntimeStreamResponseHandler<ConverseStreamResponse, ConverseStreamOutput>
      implements ConverseStreamResponseHandler {

    private final Logger eventLogger;
    private final boolean captureMessageContent;

    private StringBuilder currentText;

    private List<ToolUseBlock> tools;
    private ToolUseBlock.Builder currentTool;
    private StringBuilder currentToolArgs;

    TracingConverseStreamResponseHandler(
        ConverseStreamResponseHandler delegate, Logger eventLogger, boolean captureMessageContent) {
      super(delegate);
      this.eventLogger = eventLogger;
      this.captureMessageContent = captureMessageContent;
    }

    @Override
    protected void handleEvent(ConverseStreamOutput event) {
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
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static class TracingInvokeModelWithResponseStreamResponseHandler
      extends BedrockRuntimeStreamResponseHandler<
          InvokeModelWithResponseStreamResponse, ResponseStream>
      implements InvokeModelWithResponseStreamResponseHandler {

    private final Logger eventLogger;
    private final boolean captureMessageContent;
    private final String requestModel;

    @Nullable private StringBuilder currentText;

    @Nullable private List<ToolUseBlock> tools;
    @Nullable private ToolUseBlock.Builder currentTool;
    @Nullable private StringBuilder currentInputJson;

    private int inputTokens;
    private int outputTokens;

    TracingInvokeModelWithResponseStreamResponseHandler(
        InvokeModelWithResponseStreamResponseHandler delegate,
        Logger eventLogger,
        boolean captureMessageContent,
        String requestModel) {
      super(delegate);
      this.eventLogger = eventLogger;
      this.captureMessageContent = captureMessageContent;
      this.requestModel = requestModel;
    }

    @Override
    protected void handleEvent(ResponseStream event) {
      if (!(event instanceof PayloadPart)) {
        return;
      }
      Document result = deserializeDocument(((PayloadPart) event).bytes().asByteArrayUnsafe());
      if (requestModel.startsWith("amazon.titan")) {
        handleEventAmazonTitan(result);
      } else if (requestModel.startsWith("amazon.nova")) {
        handleEventAmazonNova(result);
      } else if (requestModel.startsWith("anthropic.claude")) {
        handleEventAnthropicClaude(result);
      }
    }

    private void handleEventAmazonTitan(Document result) {
      if (captureMessageContent) {
        Document resultText = result.asMap().get("outputText");
        if (resultText != null && resultText.isString()) {
          if (currentText == null) {
            currentText = new StringBuilder();
          }
          currentText.append(resultText.asString());
        }
      }
      // In practice, first event has the input tokens and last the output.
      Document inputTokens = result.asMap().get("inputTextTokenCount");
      if (inputTokens != null && inputTokens.isNumber()) {
        this.inputTokens = inputTokens.asNumber().intValue();
      }
      Document outputTokens = result.asMap().get("totalOutputTextTokenCount");
      if (outputTokens != null && outputTokens.isNumber()) {
        this.outputTokens = outputTokens.asNumber().intValue();
      }
      Document stopReasonDoc = result.asMap().get("completionReason");
      if (stopReasonDoc != null && stopReasonDoc.isString()) {
        String stopReason = stopReasonDoc.asString();
        if (stopReasons == null) {
          stopReasons = new ArrayList<>();
        }
        stopReasons.add(stopReason);
        // There's no indication of the final event other than completion reason. Emit the event
        // and finish up.
        newEvent(otelContext, eventLogger)
            .setAttribute(EVENT_NAME, "gen_ai.choice")
            .setBody(convertMessageData(currentText, null, 0, stopReason, captureMessageContent))
            .emit();
        this.usage =
            TokenUsage.builder()
                .inputTokens(this.inputTokens)
                .outputTokens(this.outputTokens)
                .build();
      }
    }

    private void handleEventAmazonNova(Document result) {
      if (result.asMap().get("messageStart") != null) {
        if (captureMessageContent) {
          if (currentText == null) {
            currentText = new StringBuilder();
          }
          currentText.setLength(0);
        }
        return;
      }

      Document contentBlockStart = result.asMap().get("contentBlockStart");
      if (contentBlockStart != null && contentBlockStart.isMap()) {
        Document start = contentBlockStart.asMap().get("start");
        if (start != null && start.isMap()) {
          Document toolUse = start.asMap().get("toolUse");
          if (toolUse != null && toolUse.isMap()) {
            currentTool = ToolUseBlock.builder();
            handleToolUseAmazonNova(toolUse, currentTool);
          }
        }
        return;
      }
      Document contentBlockDelta = result.asMap().get("contentBlockDelta");
      if (contentBlockDelta != null && contentBlockDelta.isMap()) {
        Document delta = contentBlockDelta.asMap().get("delta");
        if (delta == null || !delta.isMap()) {
          return;
        }
        if (captureMessageContent) {
          Document text = delta.asMap().get("text");
          if (text != null && text.isString()) {
            currentText.append(text.asString());
          }
        }
        Document toolUse = delta.asMap().get("toolUse");
        if (toolUse != null && toolUse.isMap()) {
          handleToolUseAmazonNova(toolUse, currentTool);
        }
        return;
      }
      if (result.asMap().get("contentBlockStop") != null && currentTool != null) {
        if (tools == null) {
          tools = new ArrayList<>();
        }
        tools.add(currentTool.build());
        currentTool = null;
      }
      Document messageStop = result.asMap().get("messageStop");
      if (messageStop != null && messageStop.isMap()) {
        Document stopReasonDoc = messageStop.asMap().get("stopReason");
        if (stopReasonDoc == null || !stopReasonDoc.isString()) {
          return;
        }
        if (stopReasons == null) {
          stopReasons = new ArrayList<>();
        }
        String stopReason = stopReasonDoc.asString();
        stopReasons.add(stopReason);
        newEvent(otelContext, eventLogger)
            .setAttribute(EVENT_NAME, "gen_ai.choice")
            .setBody(convertMessageData(currentText, tools, 0, stopReason, captureMessageContent))
            .emit();
        return;
      }
      Document metadata = result.asMap().get("metadata");
      if (metadata != null && metadata.isMap()) {
        Document usage = metadata.asMap().get("usage");
        if (usage == null || !usage.isMap()) {
          return;
        }
        Document inputTokens = usage.asMap().get("inputTokens");
        Document outputTokens = usage.asMap().get("outputTokens");
        if (inputTokens != null
            && inputTokens.isNumber()
            && outputTokens != null
            && outputTokens.isNumber()) {
          this.usage =
              TokenUsage.builder()
                  .inputTokens(inputTokens.asNumber().intValue())
                  .outputTokens(outputTokens.asNumber().intValue())
                  .build();
        }
      }
    }

    private void handleEventAnthropicClaude(Document result) {
      Document type = result.asMap().get("type");
      if (type == null || !type.isString()) {
        return;
      }
      switch (type.asString()) {
        case "message_start":
          {
            if (captureMessageContent) {
              if (currentText == null) {
                currentText = new StringBuilder();
              }
              currentText.setLength(0);
            }
            Document message = result.asMap().get("message");
            if (message == null || !message.isMap()) {
              return;
            }
            Document usage = message.asMap().get("usage");
            if (usage != null && usage.isMap()) {
              Document inputTokens = usage.asMap().get("input_tokens");
              if (inputTokens != null && inputTokens.isNumber()) {
                this.inputTokens = inputTokens.asNumber().intValue();
              }
              Document outputTokens = usage.asMap().get("output_tokens");
              if (outputTokens != null && outputTokens.isNumber()) {
                this.outputTokens = outputTokens.asNumber().intValue();
              }
            }
            return;
          }
        case "content_block_start":
          {
            Document contentBlock = result.asMap().get("content_block");
            if (contentBlock == null || !contentBlock.isMap()) {
              return;
            }
            Document contentBlockType = contentBlock.asMap().get("type");
            if (contentBlockType == null || !contentBlockType.isString()) {
              return;
            }
            if (contentBlockType.asString().equals("tool_use")) {
              currentTool = ToolUseBlock.builder();
              handleToolUseAnthropicCloud(contentBlock, currentTool);
            }
            return;
          }
        case "content_block_delta":
          {
            Document delta = result.asMap().get("delta");
            if (delta == null || !delta.isMap()) {
              return;
            }
            Document deltaType = delta.asMap().get("type");
            if (deltaType == null || !deltaType.isString()) {
              return;
            }
            switch (deltaType.asString()) {
              case "text_delta":
                {
                  if (captureMessageContent) {
                    Document text = delta.asMap().get("text");
                    if (text != null && text.isString()) {
                      currentText.append(text.asString());
                    }
                  }
                  return;
                }
              case "input_json_delta":
                {
                  Document json = delta.asMap().get("partial_json");
                  if (json != null && json.isString()) {
                    if (currentInputJson == null) {
                      currentInputJson = new StringBuilder();
                    }
                    currentInputJson.append(json.asString());
                  }
                  return;
                }
              default:
                // fallthrough
            }
            return;
          }
        case "content_block_stop":
          {
            if (currentTool != null) {
              if (currentInputJson != null) {
                currentTool.input(deserializeDocument(currentInputJson.toString()));
                currentInputJson.setLength(0);
              }
              if (tools == null) {
                tools = new ArrayList<>();
              }
              tools.add(currentTool.build());
              currentTool = null;
            }
            return;
          }
        case "message_delta":
          {
            Document delta = result.asMap().get("delta");
            if (delta != null && delta.isMap()) {
              Document stopReasonDoc = delta.asMap().get("stop_reason");
              if (stopReasonDoc != null && stopReasonDoc.isString()) {
                String stopReason = stopReasonDoc.asString();
                if (stopReasons == null) {
                  stopReasons = new ArrayList<>();
                }
                stopReasons.add(stopReason);
                newEvent(otelContext, eventLogger)
                    .setAttribute(EVENT_NAME, "gen_ai.choice")
                    .setBody(
                        convertMessageData(
                            currentText, tools, 0, stopReason, captureMessageContent))
                    .emit();
              }
            }
            Document usage = result.asMap().get("usage");
            if (usage != null && usage.isMap()) {
              Document outputTokens = usage.asMap().get("output_tokens");
              if (outputTokens != null && outputTokens.isNumber()) {
                this.outputTokens = outputTokens.asNumber().intValue();
              }
            }
            this.usage =
                TokenUsage.builder()
                    .inputTokens(this.inputTokens)
                    .outputTokens(this.outputTokens)
                    .build();
            return;
          }
        default:
          return;
      }
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

  private static void handleToolUseAmazonNova(Document toolUse, ToolUseBlock.Builder currentTool) {
    Document toolUseId = toolUse.asMap().get("toolUseId");
    if (toolUseId != null && toolUseId.isString()) {
      currentTool.toolUseId(toolUseId.asString());
    }
    Document name = toolUse.asMap().get("name");
    if (name != null && name.isString()) {
      currentTool.name(name.asString());
    }
    Document input = toolUse.asMap().get("input");
    if (input != null) {
      Document parsedInput;
      if (input.isString()) {
        parsedInput = deserializeDocument(input.asString());
      } else {
        parsedInput = input;
      }
      currentTool.input(parsedInput);
    }
  }

  private static void handleToolUseAnthropicCloud(
      Document toolUse, ToolUseBlock.Builder currentTool) {
    Document toolUseId = toolUse.asMap().get("id");
    if (toolUseId != null && toolUseId.isString()) {
      currentTool.toolUseId(toolUseId.asString());
    }
    Document name = toolUse.asMap().get("name");
    if (name != null && name.isString()) {
      currentTool.name(name.asString());
    }
    Document input = toolUse.asMap().get("input");
    if (input != null) {
      currentTool.input(input);
    }
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

  private static Document deserializeDocument(byte[] json) {
    JsonNode node = JSON_PARSER.parse(json);
    return node.visit(DOCUMENT_UNMARSHALLER);
  }
}
