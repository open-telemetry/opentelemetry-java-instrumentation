/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.dubbo.apache.v2_7.common;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;

public final class DubboHelper {

  private DubboHelper() {}

  public static void prepareSpan(Span span, String methodName, Invoker<?> invoker) {
    span.setAttribute(
        SemanticAttributes.RPC_SERVICE, invoker.getInterface().getSimpleName() + ":" + methodName);
    if (methodName != null) {
      span.setAttribute(SemanticAttributes.RPC_METHOD, methodName);
    }
  }

  public static StatusCode statusFromResult(Result result) {
    return !result.hasException() ? StatusCode.UNSET : StatusCode.ERROR;
  }
}
