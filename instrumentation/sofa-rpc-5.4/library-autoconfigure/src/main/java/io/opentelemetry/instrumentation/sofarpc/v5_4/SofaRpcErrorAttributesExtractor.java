/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofarpc.v5_4;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import com.alipay.sofa.rpc.core.response.SofaResponse;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.ErrorAttributes;
import javax.annotation.Nullable;

/**
 * Extracts error.type attribute for SOFARPC spans.
 *
 * <p>This extractor sets the error.type attribute based on the exception passed to {@link
 * AttributesExtractor#onEnd(AttributesBuilder, Context, Object, Object, Throwable)}. If an
 * exception is present, it uses the exception's class name as the error type, which follows the
 * OpenTelemetry semantic conventions for error reporting.
 *
 * <p>The error type is extracted from the exception's class name (e.g.,
 * "java.lang.IllegalStateException") to provide low-cardinality error classification.
 */
final class SofaRpcErrorAttributesExtractor
    implements AttributesExtractor<SofaRpcRequest, SofaResponse> {

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, SofaRpcRequest request) {
    // No start attributes
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      SofaRpcRequest request,
      @Nullable SofaResponse response,
      @Nullable Throwable error) {
    if (error != null) {
      // Use exception class name as error type
      internalSet(attributes, ErrorAttributes.ERROR_TYPE, error.getClass().getName());
    }
  }
}
