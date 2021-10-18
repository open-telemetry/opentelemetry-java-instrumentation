/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ws;

import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesExtractor;
import javax.annotation.Nullable;

public class SpringWsCodeAttributesExtractor
    extends CodeAttributesExtractor<SpringWsRequest, Void> {

  @Override
  protected Class<?> codeClass(SpringWsRequest request) {
    return request.getCodeClass();
  }

  @Override
  protected String methodName(SpringWsRequest request) {
    return request.getMethodName();
  }

  @Override
  @Nullable
  protected String filePath(SpringWsRequest request) {
    return null;
  }

  @Override
  @Nullable
  protected Long lineNumber(SpringWsRequest request) {
    return null;
  }
}
