/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.common;

import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesExtractor;
import javax.annotation.Nullable;

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
  @Nullable
  protected String filePath(JaxWsRequest request) {
    return null;
  }

  @Override
  @Nullable
  protected Long lineNumber(JaxWsRequest request) {
    return null;
  }
}
