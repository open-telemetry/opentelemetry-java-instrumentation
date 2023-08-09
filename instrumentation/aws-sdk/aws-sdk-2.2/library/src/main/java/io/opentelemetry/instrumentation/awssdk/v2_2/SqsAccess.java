/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import javax.annotation.Nullable;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;

// helper class for calling methods that use sqs types in SqsImpl
// if SqsImpl is not present these methods are no op
final class SqsAccess {
  private SqsAccess() {}

  private static final boolean enabled = PluginImplUtil.isImplPresent("SqsImpl");

  @NoMuzzle
  static boolean afterReceiveMessageExecution(
      Context.AfterExecution context,
      ExecutionAttributes executionAttributes,
      TracingExecutionInterceptor config) {
    return enabled && SqsImpl.afterReceiveMessageExecution(context, executionAttributes, config);
  }

  /**
   * Returns {@code null} (not the unmodified {@code request}!) if nothing matched, so that other
   * handling can be tried.
   */
  @Nullable
  @NoMuzzle
  static SdkRequest modifyRequest(
      SdkRequest request,
      io.opentelemetry.context.Context otelContext,
      boolean useXrayPropagator,
      TextMapPropagator messagingPropagator) {
    return enabled
        ? SqsImpl.modifyRequest(request, otelContext, useXrayPropagator, messagingPropagator)
        : null;
  }
}
