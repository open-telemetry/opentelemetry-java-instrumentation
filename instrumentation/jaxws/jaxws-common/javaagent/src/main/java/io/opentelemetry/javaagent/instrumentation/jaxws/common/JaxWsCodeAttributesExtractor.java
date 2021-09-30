/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.common;

import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesExtractor;
import org.checkerframework.checker.nullness.qual.Nullable;

public class JaxWsCodeAttributesExtractor extends CodeAttributesExtractor<JaxWsRequest, Void> {

  @Override
  protected Class<?> codeClass(JaxWsRequest request) {
    return request.codeClass();
  }

  @Override
  protected String methodName(JaxWsRequest request) {
    return request.methodName();
  }

  @Override
  protected @Nullable String filePath(JaxWsRequest request) {
    return null;
  }

  @Override
  protected @Nullable Long lineNumber(JaxWsRequest request) {
    return null;
  }
}
