/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.common;

import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;

public class JaxWsCodeAttributesGetter implements CodeAttributesGetter<JaxWsRequest> {

  @Override
  public Class<?> getCodeClass(JaxWsRequest request) {
    return request.codeClass();
  }

  @Override
  public String getMethodName(JaxWsRequest request) {
    return request.methodName();
  }
}
