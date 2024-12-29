/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import software.amazon.awssdk.core.SdkRequest;

final class SnsAccess {
  private SnsAccess() {}

  private static final boolean enabled = PluginImplUtil.isImplPresent("SnsImpl");

  @NoMuzzle
  public static SdkRequest modifyRequest(
      SdkRequest request, Context otelContext, TextMapPropagator messagingPropagator) {
    return enabled ? SnsImpl.modifyRequest(request, otelContext, messagingPropagator) : null;
  }
}
