/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.struts2;

import com.opensymphony.xwork2.ActionInvocation;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesGetter;

public class StrutsCodeAttributesGetter implements CodeAttributesGetter<ActionInvocation> {

  @Override
  public Class<?> codeClass(ActionInvocation actionInvocation) {
    return actionInvocation.getAction().getClass();
  }

  @Override
  public String methodName(ActionInvocation actionInvocation) {
    return actionInvocation.getProxy().getMethod();
  }
}
