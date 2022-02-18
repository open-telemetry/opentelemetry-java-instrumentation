/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.common;

import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesGetter;

public class JaxWsCodeAttributesGetter implements CodeAttributesGetter<JaxWsRequest> {

  @Override
  public Class<?> codeClass(JaxWsRequest request) {
    return request.codeClass();
  }

  @Override
  public String methodName(JaxWsRequest request) {
    return request.methodName();
  }
}
