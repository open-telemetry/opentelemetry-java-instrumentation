/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.code;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.semconv.CodeAttributes;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/general/attributes.md#source-code-attributes">source
 * code attributes</a>.
 */
public final class CodeAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  // copied from CodeIncubatingAttributes
  private static final AttributeKey<String> CODE_NAMESPACE =
      AttributeKey.stringKey("code.namespace");
  private static final AttributeKey<String> CODE_FUNCTION = AttributeKey.stringKey("code.function");

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

      if (SemconvStability.isEmitOldCodeSemconv()) {
        internalSet(attributes, CODE_NAMESPACE, cls.getName());
      }
    }
    String methodName = getter.getMethodName(request);
    if (methodName != null) {
      if (sb.length() > 0) {
        sb.append(".");
      }
      sb.append(methodName);
      if (SemconvStability.isEmitOldCodeSemconv()) {
        internalSet(attributes, CODE_FUNCTION, methodName);
      }
    }
    if (SemconvStability.isEmitStableCodeSemconv() && sb.length() > 0) {
      internalSet(attributes, CodeAttributes.CODE_FUNCTION_NAME, sb.toString());
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
