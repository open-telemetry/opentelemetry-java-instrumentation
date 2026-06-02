/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grails.v3_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;

class GrailsCodeAttributesGetter implements CodeAttributesGetter<HandlerData> {

  @Override
  public Class<?> getCodeClass(HandlerData handlerData) {
    return handlerData.getController().getClass();
  }

  @Override
  public String getMethodName(HandlerData handlerData) {
    return handlerData.getAction();
  }
}
