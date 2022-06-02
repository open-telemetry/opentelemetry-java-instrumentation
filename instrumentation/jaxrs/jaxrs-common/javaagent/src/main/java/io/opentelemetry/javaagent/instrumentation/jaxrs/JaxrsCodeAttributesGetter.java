/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs;

import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesGetter;

public class JaxrsCodeAttributesGetter implements CodeAttributesGetter<HandlerData> {

  @Override
  public Class<?> codeClass(HandlerData handlerData) {
    return handlerData.codeClass();
  }

  @Override
  public String methodName(HandlerData handlerData) {
    return handlerData.methodName();
  }
}
