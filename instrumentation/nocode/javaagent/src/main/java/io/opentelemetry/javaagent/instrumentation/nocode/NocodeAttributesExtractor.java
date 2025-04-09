/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nocode;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.javaagent.bootstrap.nocode.NocodeExpression;
import java.util.Map;
import javax.annotation.Nullable;

class NocodeAttributesExtractor implements AttributesExtractor<NocodeMethodInvocation, Object> {
  private final AttributesExtractor<ClassAndMethod, Object> codeExtractor;

  public NocodeAttributesExtractor() {
    codeExtractor = CodeAttributesExtractor.create(ClassAndMethod.codeAttributesGetter());
  }

  @Override
  public void onStart(
      AttributesBuilder attributesBuilder, Context context, NocodeMethodInvocation mi) {
    codeExtractor.onStart(attributesBuilder, context, mi.getClassAndMethod());

    Map<String, NocodeExpression> attributes = mi.getRuleAttributes();
    for (String key : attributes.keySet()) {
      NocodeExpression expression = attributes.get(key);
      Object value = mi.evaluate(expression);
      if (value instanceof Long
          || value instanceof Integer
          || value instanceof Short
          || value instanceof Byte) {
        attributesBuilder.put(key, ((Number) value).longValue());
      } else if (value instanceof Float || value instanceof Double) {
        attributesBuilder.put(key, ((Number) value).doubleValue());
      } else if (value instanceof Boolean) {
        attributesBuilder.put(key, (Boolean) value);
      } else if (value != null) {
        attributesBuilder.put(key, value.toString());
      }
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributesBuilder,
      Context context,
      NocodeMethodInvocation nocodeMethodInvocation,
      @Nullable Object unused,
      @Nullable Throwable throwable) {
    codeExtractor.onEnd(
        attributesBuilder, context, nocodeMethodInvocation.getClassAndMethod(), unused, throwable);
  }
}
