/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs;

import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;

public class JaxrsCodeAttributesGetter implements CodeAttributesGetter<HandlerData> {

  @Override
  public Class<?> getCodeClass(HandlerData handlerData) {
    return handlerData.codeClass();
  }

  @Override
  public String getMethodName(HandlerData handlerData) {
    return handlerData.methodName();
  }
}
