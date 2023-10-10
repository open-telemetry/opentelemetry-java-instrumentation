/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.code;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.SemanticAttributes;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/general/attributes.md#source-code-attributes">source
 * code attributes</a>.
 */
public final class CodeAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  /** Creates the code attributes extractor. */
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      CodeAttributesGetter<REQUEST> getter) {
    return new CodeAttributesExtractor<>(getter);
  }

  private final CodeAttributesGetter<REQUEST> getter;

  private CodeAttributesExtractor(CodeAttributesGetter<REQUEST> getter) {
    this.getter = getter;
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    Class<?> cls = getter.getCodeClass(request);
    if (cls != null) {
      internalSet(attributes, SemanticAttributes.CODE_NAMESPACE, cls.getName());
    }
    internalSet(attributes, SemanticAttributes.CODE_FUNCTION, getter.getMethodName(request));
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {}
}
