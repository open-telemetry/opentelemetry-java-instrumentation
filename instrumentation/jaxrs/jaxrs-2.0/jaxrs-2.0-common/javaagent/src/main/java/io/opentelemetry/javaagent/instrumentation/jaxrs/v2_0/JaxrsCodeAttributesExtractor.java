/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesExtractor;
import org.checkerframework.checker.nullness.qual.Nullable;

public class JaxrsCodeAttributesExtractor extends CodeAttributesExtractor<HandlerData, Void> {
  @Override
  protected Class<?> codeClass(HandlerData handlerData) {
    return handlerData.codeClass();
  }

  @Override
  protected String methodName(HandlerData handlerData) {
    return handlerData.methodName();
  }

  @Override
  protected @Nullable String filePath(HandlerData handlerData) {
    return null;
  }

  @Override
  protected @Nullable Long lineNumber(HandlerData handlerData) {
    return null;
  }
}
