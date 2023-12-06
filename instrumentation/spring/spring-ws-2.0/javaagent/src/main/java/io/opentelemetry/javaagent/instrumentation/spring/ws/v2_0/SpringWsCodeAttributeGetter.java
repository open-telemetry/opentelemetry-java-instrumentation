/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ws.v2_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributeGetter;

public class SpringWsCodeAttributeGetter implements CodeAttributeGetter<SpringWsRequest> {

  @Override
  public Class<?> getCodeClass(SpringWsRequest request) {
    return request.getCodeClass();
  }

  @Override
  public String getMethodName(SpringWsRequest request) {
    return request.getMethodName();
  }
}
