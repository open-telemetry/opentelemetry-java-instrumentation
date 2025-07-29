/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.struts.v2_3;

import com.opensymphony.xwork2.ActionInvocation;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;

public class StrutsCodeAttributesGetter implements CodeAttributesGetter<ActionInvocation> {

  @Override
  public Class<?> getCodeClass(ActionInvocation actionInvocation) {
    return actionInvocation.getAction().getClass();
  }

  @Override
  public String getMethodName(ActionInvocation actionInvocation) {
    return actionInvocation.getProxy().getMethod();
  }
}
