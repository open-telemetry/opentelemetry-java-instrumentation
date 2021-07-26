/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support;

/** A builder of {@link MethodSpanAttributesExtractor}. */
public final class MethodSpanAttributesExtractorBuilder<REQUEST, RESPONSE> {
  MethodExtractor<REQUEST> methodExtractor;
  MethodArgumentsExtractor<REQUEST> methodArgumentsExtractor;
  ParameterAttributeNamesExtractor parameterAttributeNamesExtractor;

  public MethodSpanAttributesExtractorBuilder(MethodExtractor<REQUEST> methodExtractor) {
    this.methodExtractor = methodExtractor;
  }

  /**
   * Returns a new {@link MethodSpanAttributesExtractor} that extracts {@link
   * io.opentelemetry.api.common.Attributes} from the arguments passed to the traced method.
   */
  public MethodSpanAttributesExtractor<REQUEST, RESPONSE> newMethodSpanAttributesExtractor(
      ParameterAttributeNamesExtractor parameterAttributeNamesExtractor,
      MethodArgumentsExtractor<REQUEST> methodArgumentsExtractor) {
    this.parameterAttributeNamesExtractor = parameterAttributeNamesExtractor;
    this.methodArgumentsExtractor = methodArgumentsExtractor;
    return new MethodSpanAttributesExtractor<>(this);
  }
}
