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
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/general/attributes.md#source-code-attributes">source
 * code attributes</a>.
 */
public final class CodeAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  // copied from CodeIncubatingAttributes
  private static final AttributeKey<String> CODE_FUNCTION_NAME =
      AttributeKey.stringKey("code.function.name");

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
      sb.append(cls.getName()).append(".");
    }
    sb.append(getter.getMethodName(request));
    internalSet(attributes, CODE_FUNCTION_NAME, sb.toString());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {}
}
