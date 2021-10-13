/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.code;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/span-general.md#source-code-attributes">source
 * code attributes</a>.
 */
public abstract class CodeAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  @Override
  public final void onStart(AttributesBuilder attributes, REQUEST request) {
    Class<?> cls = codeClass(request);
    if (cls != null) {
      set(attributes, SemanticAttributes.CODE_NAMESPACE, cls.getName());
    }
    set(attributes, SemanticAttributes.CODE_FUNCTION, methodName(request));
    set(attributes, SemanticAttributes.CODE_FILEPATH, filePath(request));
    set(attributes, SemanticAttributes.CODE_LINENO, lineNumber(request));
  }

  @Override
  public final void onEnd(
      AttributesBuilder attributes,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {}

  @Nullable
  protected abstract Class<?> codeClass(REQUEST request);

  @Nullable
  protected abstract String methodName(REQUEST request);

  @Nullable
  protected abstract String filePath(REQUEST request);

  @Nullable
  protected abstract Long lineNumber(REQUEST request);
}
