/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import java.util.List;
import javax.annotation.Nullable;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.SdkResponse;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;

final class BedrockRuntimeAccess {
  private BedrockRuntimeAccess() {}

  private static final boolean enabled;

  static {
    boolean isEnabled = true;
    if (!PluginImplUtil.isImplPresent("BedrockRuntimeImpl")) {
      // Muzzle disabled the instrumentation.
      isEnabled = false;
    } else {
      try {
        Class.forName("software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest");
      } catch (ClassNotFoundException e) {
        // Application does not include library
        isEnabled = false;
      }
    }
    enabled = isEnabled;
  }

  @NoMuzzle
  static boolean isBedrockRuntimeRequest(SdkRequest request) {
    return enabled && BedrockRuntimeImpl.isBedrockRuntimeRequest(request);
  }

  @NoMuzzle
  static boolean isBedrockRuntimeResponse(SdkResponse response) {
    return enabled && BedrockRuntimeImpl.isBedrockRuntimeResponse(response);
  }

  @NoMuzzle
  static void maybeParseInvokeModelRequest(
      ExecutionAttributes executionAttributes, SdkRequest request) {
    if (enabled) {
      BedrockRuntimeImpl.maybeParseInvokeModelRequest(executionAttributes, request);
    }
  }

  @NoMuzzle
  static void maybeParseInvokeModelResponse(
      ExecutionAttributes executionAttributes, SdkResponse response) {
    if (enabled) {
      BedrockRuntimeImpl.maybeParseInvokeModelResponse(executionAttributes, response);
    }
  }

  @Nullable
  @NoMuzzle
  static String getModelId(ExecutionAttributes executionAttributes) {
    return enabled ? BedrockRuntimeImpl.getModelId(executionAttributes) : null;
  }

  @Nullable
  @NoMuzzle
  static String getOperationName(ExecutionAttributes executionAttributes) {
    return enabled ? BedrockRuntimeImpl.getOperationName(executionAttributes) : null;
  }

  @Nullable
  @NoMuzzle
  static Long getMaxTokens(ExecutionAttributes executionAttributes) {
    return enabled ? BedrockRuntimeImpl.getMaxTokens(executionAttributes) : null;
  }

  @Nullable
  @NoMuzzle
  static Double getTemperature(ExecutionAttributes executionAttributes) {
    return enabled ? BedrockRuntimeImpl.getTemperature(executionAttributes) : null;
  }

  @Nullable
  @NoMuzzle
  static Double getTopP(ExecutionAttributes executionAttributes) {
    return enabled ? BedrockRuntimeImpl.getTopP(executionAttributes) : null;
  }

  @Nullable
  @NoMuzzle
  static List<String> getStopSequences(ExecutionAttributes executionAttributes) {
    return enabled ? BedrockRuntimeImpl.getStopSequences(executionAttributes) : null;
  }

  @Nullable
  @NoMuzzle
  static List<String> getStopReasons(ExecutionAttributes executionAttributes, Response response) {
    return enabled ? BedrockRuntimeImpl.getStopReasons(executionAttributes, response) : null;
  }

  @Nullable
  @NoMuzzle
  static Long getUsageInputTokens(ExecutionAttributes executionAttributes, Response response) {
    return enabled ? BedrockRuntimeImpl.getUsageInputTokens(executionAttributes, response) : null;
  }

  @Nullable
  @NoMuzzle
  static Long getUsageOutputTokens(ExecutionAttributes executionAttributes, Response response) {
    return enabled ? BedrockRuntimeImpl.getUsageOutputTokens(executionAttributes, response) : null;
  }

  @NoMuzzle
  static void recordRequestEvents(
      Context otelContext,
      Logger eventLogger,
      ExecutionAttributes executionAttributes,
      SdkRequest request,
      boolean captureMessageContent) {
    if (enabled) {
      BedrockRuntimeImpl.recordRequestEvents(
          otelContext, eventLogger, executionAttributes, request, captureMessageContent);
    }
  }

  @NoMuzzle
  static void recordResponseEvents(
      Context otelContext,
      Logger eventLogger,
      ExecutionAttributes executionAttributes,
      SdkResponse response,
      boolean captureMessageContent) {
    if (enabled) {
      BedrockRuntimeImpl.recordResponseEvents(
          otelContext, eventLogger, executionAttributes, response, captureMessageContent);
    }
  }
}
