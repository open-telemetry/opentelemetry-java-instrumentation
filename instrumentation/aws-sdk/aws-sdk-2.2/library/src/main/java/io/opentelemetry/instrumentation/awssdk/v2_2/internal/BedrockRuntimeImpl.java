/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ImplicitContextKeyed;
import io.opentelemetry.context.Scope;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.SdkResponse;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetadataEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class BedrockRuntimeImpl {
  private BedrockRuntimeImpl() {}

  static boolean isBedrockRuntimeRequest(SdkRequest request) {
    if (request instanceof ConverseRequest) {
      return true;
    }
    if (request instanceof ConverseStreamRequest) {
      return true;
    }
    return false;
  }

  @Nullable
  static String getModelId(SdkRequest request) {
    if (request instanceof ConverseRequest) {
      return ((ConverseRequest) request).modelId();
    }
    if (request instanceof ConverseStreamRequest) {
      return ((ConverseStreamRequest) request).modelId();
    }
    return null;
  }

  @Nullable
  static Long getMaxTokens(SdkRequest request) {
    InferenceConfiguration config = null;
    if (request instanceof ConverseRequest) {
      config = ((ConverseRequest) request).inferenceConfig();
    }
    if (request instanceof ConverseStreamRequest) {
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
    }
    if (request instanceof ConverseStreamRequest) {
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
    }
    if (request instanceof ConverseStreamRequest) {
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
    }
    if (request instanceof ConverseStreamRequest) {
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
    }
    TracingConverseStreamResponseHandler streamHandler =
        TracingConverseStreamResponseHandler.fromContext(response.otelContext());
    if (streamHandler != null) {
      return streamHandler.stopReasons;
    }
    return null;
  }

  @Nullable
  static Long getUsageInputTokens(Response response) {
    SdkResponse sdkResponse = response.getSdkResponse();
    TokenUsage usage = null;
    if (sdkResponse instanceof ConverseResponse) {
      usage = ((ConverseResponse) sdkResponse).usage();
    }
    TracingConverseStreamResponseHandler streamHandler =
        TracingConverseStreamResponseHandler.fromContext(response.otelContext());
    if (streamHandler != null) {
      usage = streamHandler.usage;
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
    }
    TracingConverseStreamResponseHandler streamHandler =
        TracingConverseStreamResponseHandler.fromContext(response.otelContext());
    if (streamHandler != null) {
      usage = streamHandler.usage;
    }
    if (usage != null) {
      return integerToLong(usage.outputTokens());
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

  public static BedrockRuntimeAsyncClient wrap(BedrockRuntimeAsyncClient asyncClient) {
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
                        (ConverseStreamResponseHandler) args[1]);
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

    List<String> stopReasons;
    TokenUsage usage;

    TracingConverseStreamResponseHandler(ConverseStreamResponseHandler delegate) {
      this.delegate = delegate;
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
                if (event instanceof MessageStopEvent) {
                  if (stopReasons == null) {
                    stopReasons = new ArrayList<>();
                  }
                  stopReasons.add(((MessageStopEvent) event).stopReasonAsString());
                }
                if (event instanceof ConverseStreamMetadataEvent) {
                  usage = ((ConverseStreamMetadataEvent) event).usage();
                }
                return event;
              }));
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
  }
}
