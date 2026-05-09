/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.common.v2_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;

class JaxWsCodeAttributesGetter implements CodeAttributesGetter<JaxWsRequest> {

  @Override
  public Class<?> getCodeClass(JaxWsRequest request) {
    return request.codeClass();
  }

  @Override
  public String getMethodName(JaxWsRequest request) {
    return request.methodName();
  }
}
