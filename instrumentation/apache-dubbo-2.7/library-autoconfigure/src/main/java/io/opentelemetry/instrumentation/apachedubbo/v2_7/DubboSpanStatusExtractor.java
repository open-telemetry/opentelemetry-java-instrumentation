/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.internal.DubboStatusCodeHolder;
import javax.annotation.Nullable;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcInvocation;

enum DubboSpanStatusExtractor implements SpanStatusExtractor<DubboRequest, Result> {
  CLIENT {
    @Override
    public void extract(
        SpanStatusBuilder spanStatusBuilder,
        DubboRequest request,
        @Nullable Result response,
        @Nullable Throwable error) {

      String statusCode = resolveStatusCode(request, response, error);
      if (statusCode != null && !"OK".equals(statusCode)) {
        spanStatusBuilder.setStatus(StatusCode.ERROR);
        return;
      }

      // Fall back to default behavior (error → ERROR)
      if (error != null) {
        spanStatusBuilder.setStatus(StatusCode.ERROR);
      }
    }
  },

  SERVER {
    @Override
    public void extract(
        SpanStatusBuilder spanStatusBuilder,
        DubboRequest request,
        @Nullable Result response,
        @Nullable Throwable error) {

      String statusCode = resolveStatusCode(request, response, error);
      if (statusCode != null) {
        // Dubbo2: only specific server-side status codes are errors
        if (DubboStatusCodeConverter.isDubbo2ServerError(statusCode)
            || DubboStatusCodeConverter.isTripleServerError(statusCode)) {
          spanStatusBuilder.setStatus(StatusCode.ERROR);
          return;
        }
      }

      // Fall back to default behavior (error → ERROR)
      if (error != null) {
        spanStatusBuilder.setStatus(StatusCode.ERROR);
      }
    }
  };

  private static final VirtualField<RpcInvocation, DubboStatusCodeHolder> statusCodeField =
      VirtualField.find(RpcInvocation.class, DubboStatusCodeHolder.class);

  @Nullable
  static String resolveStatusCode(
      DubboRequest request, @Nullable Result response, @Nullable Throwable error) {
    // 1. Check VirtualField (set by javaagent bytecode instrumentation)
    DubboStatusCodeHolder holder = statusCodeField.get(request.invocation());
    if (holder != null) {
      return holder.getStatusCode();
    }

    // 2. Try to extract Triple status code from error
    String tripleStatusCode = DubboStatusCodeConverter.extractTripleStatusCode(error);
    if (tripleStatusCode != null) {
      return tripleStatusCode;
    }

    // 3. Check result exception for Triple (async case)
    if (response != null && response.hasException()) {
      tripleStatusCode = DubboStatusCodeConverter.extractTripleStatusCode(response.getException());
      if (tripleStatusCode != null) {
        return tripleStatusCode;
      }
    }

    return null;
  }
}
