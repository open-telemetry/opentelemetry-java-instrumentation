/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ws;

import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesGetter;

public class SpringWsCodeAttributesGetter implements CodeAttributesGetter<SpringWsRequest> {

  @Override
  public Class<?> codeClass(SpringWsRequest request) {
    return request.getCodeClass();
  }

  @Override
  public String methodName(SpringWsRequest request) {
    return request.getMethodName();
  }
}
