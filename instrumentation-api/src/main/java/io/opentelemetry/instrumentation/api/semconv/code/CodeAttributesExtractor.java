/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.code;

import static io.opentelemetry.semconv.CodeAttributes.CODE_FUNCTION_NAME;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://opentelemetry.io/docs/specs/semconv/general/attributes/#source-code-attributes">source
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
    StringBuilder sb = new StringBuilder();
    Class<?> cls = getter.getCodeClass(request);
    if (cls != null) {
      sb.append(cls.getName());
    }
    String methodName = getter.getMethodName(request);
    if (methodName != null) {
      if (sb.length() > 0) {
        sb.append(".");
      }
      sb.append(methodName);
    }
    if (sb.length() > 0) {
      attributes.put(CODE_FUNCTION_NAME, sb.toString());
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {}
}
