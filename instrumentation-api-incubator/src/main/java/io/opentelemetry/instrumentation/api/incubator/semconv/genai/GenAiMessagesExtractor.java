package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_INPUT_MESSAGES;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_OUTPUT_MESSAGES;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_OUTPUT_TYPE;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_PROVIDER_NAME;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_CHOICE_COUNT;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_FREQUENCY_PENALTY;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MAX_TOKENS;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_PRESENCE_PENALTY;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_SEED;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_STOP_SEQUENCES;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_TEMPERATURE;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_TOP_K;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_TOP_P;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_FINISH_REASONS;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_ID;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_MODEL;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_SYSTEM_INSTRUCTIONS;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_TOOL_DEFINITIONS;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_INPUT_TOKENS;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_OUTPUT_TOKENS;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GenAiEventName.GEN_AI_CLIENT_INFERENCE_OPERATION_DETAILS;
import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.EVENT_NAME;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.aliyun.common.JsonMarshaler;
import io.opentelemetry.instrumentation.api.aliyun.common.provider.GlobalInstanceHolder;
import io.opentelemetry.instrumentation.api.genai.MessageCaptureOptions;
import io.opentelemetry.instrumentation.api.genai.MessageCaptureOptions.CaptureMessageStrategy;
import io.opentelemetry.instrumentation.api.genai.messages.InputMessages;
import io.opentelemetry.instrumentation.api.genai.messages.OutputMessages;
import io.opentelemetry.instrumentation.api.genai.messages.SystemInstructions;
import io.opentelemetry.instrumentation.api.genai.messages.ToolDefinitions;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.log.genai.GenAiEventLoggerProvider;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

public class GenAiMessagesExtractor <REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  private static final Logger LOGGER = Logger.getLogger(GenAiMessagesExtractor.class.getName());

  /** Creates the GenAI attributes extractor. */
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      GenAiAttributesGetter<REQUEST, RESPONSE> attributesGetter,
      GenAiMessagesProvider<REQUEST, RESPONSE> messagesProvider,
      MessageCaptureOptions messageCaptureOptions,
      String instrumentationName) {
    return new GenAiMessagesExtractor<>(attributesGetter, messagesProvider, messageCaptureOptions, instrumentationName);
  }

  private final MessageCaptureOptions messageCaptureOptions;

  private final GenAiAttributesGetter<REQUEST, RESPONSE> getter;

  private final GenAiMessagesProvider<REQUEST, RESPONSE> messagesProvider;

  private final String instrumentationName;

  private final AtomicBoolean lazyInit = new AtomicBoolean(false);

  private JsonMarshaler jsonMarshaler;

  private io.opentelemetry.api.logs.Logger eventLogger;

  private GenAiMessagesExtractor(
      GenAiAttributesGetter<REQUEST, RESPONSE> getter,
      GenAiMessagesProvider<REQUEST, RESPONSE> messagesProvider,
      MessageCaptureOptions messageCaptureOptions,
      String instrumentationName) {
    this.getter = getter;
    this.messagesProvider = messagesProvider;
    this.messageCaptureOptions = messageCaptureOptions;
    this.instrumentationName = instrumentationName;
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    tryInit();
    if (CaptureMessageStrategy.SPAN_ATTRIBUTES.equals(messageCaptureOptions.captureMessageStrategy())) {
      SystemInstructions systemInstructions = messagesProvider.systemInstructions(request, null);
      if (systemInstructions != null) {
        internalSet(attributes, GEN_AI_SYSTEM_INSTRUCTIONS, toJsonString(systemInstructions.getSerializableObject()));
      }

      InputMessages inputMessages = messagesProvider.inputMessages(request, null);
      if (inputMessages != null) {
        internalSet(attributes, GEN_AI_INPUT_MESSAGES, toJsonString(inputMessages.getSerializableObject()));
      }

      ToolDefinitions toolDefinitions = messagesProvider.toolDefinitions(request, null);
      if (toolDefinitions != null) {
        internalSet(attributes, GEN_AI_TOOL_DEFINITIONS, toJsonString(toolDefinitions.getSerializableObject()));
      }
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    if (CaptureMessageStrategy.SPAN_ATTRIBUTES.equals(messageCaptureOptions.captureMessageStrategy())) {
      OutputMessages outputMessages = messagesProvider.outputMessages(request, response);
      if (outputMessages != null) {
        internalSet(attributes, GEN_AI_OUTPUT_MESSAGES, toJsonString(outputMessages.getSerializableObject()));
      }
    } else if (CaptureMessageStrategy.EVENT.equals(messageCaptureOptions.captureMessageStrategy())) {
      emitInferenceEvent(context, request, response);
    }
  }

  private void emitInferenceEvent(Context context, REQUEST request, @Nullable RESPONSE response) {
    if (eventLogger != null) {
      LogRecordBuilder builder = eventLogger.logRecordBuilder()
          .setAttribute(EVENT_NAME, GEN_AI_CLIENT_INFERENCE_OPERATION_DETAILS)
          .setContext(context);

      SystemInstructions systemInstructions = messagesProvider.systemInstructions(request,
          response);
      if (systemInstructions != null) {
        internalSetLogAttribute(builder, GEN_AI_SYSTEM_INSTRUCTIONS, toJsonString(systemInstructions.getSerializableObject()));
      }
      InputMessages inputMessages = messagesProvider.inputMessages(request, response);
      if (inputMessages != null) {
        internalSetLogAttribute(builder, GEN_AI_INPUT_MESSAGES, toJsonString(inputMessages.getSerializableObject()));
      }
      ToolDefinitions toolDefinitions = messagesProvider.toolDefinitions(request, null);
      if (toolDefinitions != null) {
        internalSetLogAttribute(builder, GEN_AI_TOOL_DEFINITIONS, toJsonString(toolDefinitions.getSerializableObject()));
      }
      OutputMessages outputMessages = messagesProvider.outputMessages(request, response);
      if (outputMessages != null) {
        internalSetLogAttribute(builder, GEN_AI_OUTPUT_MESSAGES, toJsonString(outputMessages.getSerializableObject()));
      }

      internalSetLogAttribute(builder, GEN_AI_OPERATION_NAME, getter.getOperationName(request));
      internalSetLogAttribute(builder, GEN_AI_OUTPUT_TYPE, getter.getOutputType(request));
      internalSetLogAttribute(builder, GEN_AI_REQUEST_CHOICE_COUNT, getter.getChoiceCount(request));
      internalSetLogAttribute(builder, GEN_AI_PROVIDER_NAME, getter.getSystem(request));
      internalSetLogAttribute(builder, GEN_AI_REQUEST_MODEL, getter.getRequestModel(request));
      internalSetLogAttribute(builder, GEN_AI_REQUEST_SEED, getter.getRequestSeed(request));
      internalSetLogAttribute(
          builder, GEN_AI_REQUEST_FREQUENCY_PENALTY, getter.getRequestFrequencyPenalty(request));
      internalSetLogAttribute(builder, GEN_AI_REQUEST_MAX_TOKENS, getter.getRequestMaxTokens(request));
      internalSetLogAttribute(
          builder, GEN_AI_REQUEST_PRESENCE_PENALTY, getter.getRequestPresencePenalty(request));
      internalSetLogAttribute(builder, GEN_AI_REQUEST_STOP_SEQUENCES, getter.getRequestStopSequences(request));
      internalSetLogAttribute(builder, GEN_AI_REQUEST_TEMPERATURE, getter.getRequestTemperature(request));
      internalSetLogAttribute(builder, GEN_AI_REQUEST_TOP_K, getter.getRequestTopK(request));
      internalSetLogAttribute(builder, GEN_AI_REQUEST_TOP_P, getter.getRequestTopP(request));

      List<String> finishReasons = getter.getResponseFinishReasons(request, response);
      if (finishReasons != null && !finishReasons.isEmpty()) {
        builder.setAttribute(GEN_AI_RESPONSE_FINISH_REASONS, finishReasons);
      }
      internalSetLogAttribute(builder, GEN_AI_RESPONSE_ID, getter.getResponseId(request, response));
      internalSetLogAttribute(builder, GEN_AI_RESPONSE_MODEL, getter.getResponseModel(request, response));
      internalSetLogAttribute(
          builder, GEN_AI_USAGE_INPUT_TOKENS, getter.getUsageInputTokens(request, response));
      internalSetLogAttribute(
          builder, GEN_AI_USAGE_OUTPUT_TOKENS, getter.getUsageOutputTokens(request, response));
      builder.emit();
    }
  }

  private <T> void internalSetLogAttribute(LogRecordBuilder logRecordBuilder, AttributeKey<T> key, @Nullable T value) {
    if (value == null) {
      return;
    }
    logRecordBuilder.setAttribute(key, value);
  }

  private void tryInit() {
    if (lazyInit.get()) {
      return;
    }

    if (lazyInit.compareAndSet(false, true)) {
      jsonMarshaler = GlobalInstanceHolder.getInstance(JsonMarshaler.class);
      if (jsonMarshaler == null) {
        LOGGER.log(Level.WARNING, "failed to init json marshaler, global instance is null");
      }

      GenAiEventLoggerProvider loggerProvider = GlobalInstanceHolder.getInstance(
          GenAiEventLoggerProvider.class);

      if (loggerProvider == null) {
        LOGGER.log(Level.WARNING, "failed to init event logger, logger provider is null");
        return;
      }

      eventLogger = loggerProvider.get(instrumentationName);
    }
  }

  private String toJsonString(Object object) {
    if (jsonMarshaler == null) {
      LOGGER.log(Level.INFO, "failed to serialize object, json marshaler is null");
      return null;
    }
    return jsonMarshaler.toJSONStringNonEmpty(object);
  }
}
