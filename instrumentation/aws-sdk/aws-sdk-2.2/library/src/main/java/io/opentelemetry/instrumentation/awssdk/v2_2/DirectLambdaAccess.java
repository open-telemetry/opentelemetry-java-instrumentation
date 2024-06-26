/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import software.amazon.awssdk.core.SdkRequest;

final class DirectLambdaAccess {
  private DirectLambdaAccess() {}

  private static final boolean enabled = PluginImplUtil.isImplPresent("DirectLambdaImpl");

  @NoMuzzle
  public static SdkRequest modifyRequest(
      SdkRequest request, Context otelContext) {
    return enabled ? DirectLambdaImpl.modifyRequest(request, otelContext) : null;
  }
}
