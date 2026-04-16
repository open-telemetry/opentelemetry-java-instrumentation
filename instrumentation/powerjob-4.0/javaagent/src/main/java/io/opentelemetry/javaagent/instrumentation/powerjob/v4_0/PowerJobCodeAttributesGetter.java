/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.powerjob.v4_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;

class PowerJobCodeAttributesGetter implements CodeAttributesGetter<PowerJobProcessRequest> {

  @Override
  public Class<?> getCodeClass(PowerJobProcessRequest powerJobProcessRequest) {
    return powerJobProcessRequest.getDeclaringClass();
  }

  @Override
  public String getMethodName(PowerJobProcessRequest powerJobProcessRequest) {
    return powerJobProcessRequest.getMethodName();
  }
}
