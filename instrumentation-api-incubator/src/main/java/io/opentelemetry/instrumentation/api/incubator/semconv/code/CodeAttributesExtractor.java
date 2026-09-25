/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.code;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/general/attributes.md#source-code-attributes">source
 * code attributes</a>.
 *
 * @deprecated Use {@link io.opentelemetry.instrumentation.api.semconv.code.CodeAttributesExtractor}
 *     instead. Will be removed in 3.0.
 */
@Deprecated // to be removed in 3.0
public final class CodeAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  /** Creates the code attributes extractor. */
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      CodeAttributesGetter<REQUEST> getter) {
    return new CodeAttributesExtractor<>(getter);
  }

  private final AttributesExtractor<REQUEST, RESPONSE> delegate;

  private CodeAttributesExtractor(CodeAttributesGetter<REQUEST> getter) {
    delegate =
        io.opentelemetry.instrumentation.api.semconv.code.CodeAttributesExtractor.create(getter);
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    delegate.onStart(attributes, parentContext, request);
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    delegate.onEnd(attributes, context, request, response, error);
  }
}
