/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jsonrpc4j.v1_3;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import javax.annotation.Nullable;

public enum JsonRpcServerSpanStatusExtractor
    implements SpanStatusExtractor<JsonRpcRequest, JsonRpcResponse> {
  INSTANCE;

  /** Extracts the status from the response and sets it to the {@code spanStatusBuilder}. */
  @Override
  public void extract(
      SpanStatusBuilder spanStatusBuilder,
      JsonRpcRequest jsonRpcRequest,
      @Nullable JsonRpcResponse jsonRpcResponse,
      @Nullable Throwable error) {
    if (error == null) {
      spanStatusBuilder.setStatus(StatusCode.OK);
    }

    // treat client invalid input as OK
    if (error instanceof JsonParseException || error instanceof JsonMappingException) {
      spanStatusBuilder.setStatus(StatusCode.OK);
    }

    spanStatusBuilder.setStatus(StatusCode.ERROR);
  }
}
