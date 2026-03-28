/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableRpcSemconv;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.internal.DubboStatusCodeHolder;
import javax.annotation.Nullable;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcInvocation;

final class DubboAttributesExtractor implements AttributesExtractor<DubboRequest, Result> {

  private static final AttributeKey<String> RPC_RESPONSE_STATUS_CODE =
      AttributeKey.stringKey("rpc.response.status_code");

  private static final VirtualField<RpcInvocation, DubboStatusCodeHolder> statusCodeField =
      VirtualField.find(RpcInvocation.class, DubboStatusCodeHolder.class);

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, DubboRequest request) {
    // status code is only available at response time
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      DubboRequest request,
      @Nullable Result response,
      @Nullable Throwable error) {

    if (!emitStableRpcSemconv()) {
      return;
    }

    String statusCode = resolveStatusCode(request, response, error);
    if (statusCode != null) {
      attributes.put(RPC_RESPONSE_STATUS_CODE, statusCode);
    }
  }

  @Nullable
  private static String resolveStatusCode(
      DubboRequest request, @Nullable Result response, @Nullable Throwable error) {
    // 1. Check VirtualField for status code set by javaagent bytecode instrumentation
    DubboStatusCodeHolder holder = statusCodeField.get(request.invocation());
    if (holder != null) {
      return holder.getStatusCode();
    }

    // 2. Try to extract Triple status code from the error throwable
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

    // 4. If no error at all, the call succeeded → status is OK
    if (error == null && (response == null || !response.hasException())) {
      return "OK";
    }

    // 5. Error present but no specific status code available (Dubbo2 without javaagent)
    return null;
  }
}
