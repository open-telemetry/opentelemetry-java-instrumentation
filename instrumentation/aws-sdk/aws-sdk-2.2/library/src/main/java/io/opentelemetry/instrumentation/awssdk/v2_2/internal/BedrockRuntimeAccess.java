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

  @Nullable
  @NoMuzzle
  static String getModelId(SdkRequest request) {
    return enabled ? BedrockRuntimeImpl.getModelId(request) : null;
  }

  @Nullable
  @NoMuzzle
  static Long getMaxTokens(SdkRequest request) {
    return enabled ? BedrockRuntimeImpl.getMaxTokens(request) : null;
  }

  @Nullable
  @NoMuzzle
  static Double getTemperature(SdkRequest request) {
    return enabled ? BedrockRuntimeImpl.getTemperature(request) : null;
  }

  @Nullable
  @NoMuzzle
  static Double getTopP(SdkRequest request) {
    return enabled ? BedrockRuntimeImpl.getTopP(request) : null;
  }

  @Nullable
  @NoMuzzle
  static List<String> getStopSequences(SdkRequest request) {
    return enabled ? BedrockRuntimeImpl.getStopSequences(request) : null;
  }

  @Nullable
  @NoMuzzle
  static List<String> getStopReasons(Response response) {
    return enabled ? BedrockRuntimeImpl.getStopReasons(response) : null;
  }

  @Nullable
  @NoMuzzle
  static Long getUsageInputTokens(Response response) {
    return enabled ? BedrockRuntimeImpl.getUsageInputTokens(response) : null;
  }

  @Nullable
  @NoMuzzle
  static Long getUsageOutputTokens(Response response) {
    return enabled ? BedrockRuntimeImpl.getUsageOutputTokens(response) : null;
  }

  @NoMuzzle
  static void recordRequestEvents(
      Context otelContext, Logger eventLogger, SdkRequest request, boolean captureMessageContent) {
    if (enabled) {
      BedrockRuntimeImpl.recordRequestEvents(
          otelContext, eventLogger, request, captureMessageContent);
    }
  }

  @NoMuzzle
  static void recordResponseEvents(
      Context otelContext,
      Logger eventLogger,
      SdkResponse response,
      boolean captureMessageContent) {
    if (enabled) {
      BedrockRuntimeImpl.recordResponseEvents(
          otelContext, eventLogger, response, captureMessageContent);
    }
  }
}
