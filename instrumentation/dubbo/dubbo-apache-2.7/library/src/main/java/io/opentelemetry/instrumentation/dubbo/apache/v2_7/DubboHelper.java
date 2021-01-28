/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.dubbo.apache.v2_7;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.apache.dubbo.rpc.Result;

class DubboHelper {

  private DubboHelper() {}

  public static void prepareSpan(Span span, String interfaceName, String methodName) {
    span.setAttribute(SemanticAttributes.RPC_SERVICE, interfaceName);
    span.setAttribute(SemanticAttributes.RPC_METHOD, methodName);
  }

  public static String getSpanName(String interfaceName, String methodName) {
    return interfaceName + "/" + methodName;
  }

  public static StatusCode statusFromResult(Result result) {
    return !result.hasException() ? StatusCode.UNSET : StatusCode.ERROR;
  }
}
