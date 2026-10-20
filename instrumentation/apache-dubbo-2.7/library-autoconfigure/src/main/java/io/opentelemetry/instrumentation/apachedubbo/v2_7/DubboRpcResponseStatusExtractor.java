/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.internal.RpcSemconvSelection;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.internal.OpenTelemetryConfigurable;
import javax.annotation.Nullable;
import org.apache.dubbo.rpc.Result;

final class DubboRpcResponseStatusExtractor
    implements AttributesExtractor<DubboRequest, Result>,
        SpanStatusExtractor<DubboRequest, Result>,
        OpenTelemetryConfigurable {

  private static final AttributeKey<String> RPC_RESPONSE_STATUS_CODE =
      AttributeKey.stringKey("rpc.response.status_code");

  private final boolean isClient;
  private final RpcSemconvSelection semconvSelection = new RpcSemconvSelection();

  DubboRpcResponseStatusExtractor(boolean isClient) {
    this.isClient = isClient;
  }

  @Override
  public void internalInitialize(OpenTelemetry openTelemetry) {
    semconvSelection.initialize(openTelemetry);
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, DubboRequest request) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      DubboRequest request,
      @Nullable Result response,
      @Nullable Throwable error) {
    if (!semconvSelection.get().emitPreview()) {
      return;
    }
    String statusCode = DubboRpcStatus.getStatusCode(response, error);
    if (statusCode == null) {
      return;
    }
    attributes.put(RPC_RESPONSE_STATUS_CODE, statusCode);
    if (!DubboRpcStatus.isError(statusCode, isClient)) {
      attributes.remove(ERROR_TYPE);
    }
  }

  @Override
  public void extract(
      SpanStatusBuilder spanStatusBuilder,
      DubboRequest request,
      @Nullable Result response,
      @Nullable Throwable error) {
    if (!semconvSelection.get().emitPreview()) {
      SpanStatusExtractor.<DubboRequest, Result>getDefault()
          .extract(spanStatusBuilder, request, response, error);
      return;
    }
    String statusCode = DubboRpcStatus.getStatusCode(response, error);
    if (statusCode == null) {
      SpanStatusExtractor.<DubboRequest, Result>getDefault()
          .extract(spanStatusBuilder, request, response, error);
      return;
    }
    spanStatusBuilder.setStatus(
        DubboRpcStatus.isError(statusCode, isClient) ? StatusCode.ERROR : StatusCode.UNSET);
  }
}
