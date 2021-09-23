/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.struts2;

import com.opensymphony.xwork2.ActionInvocation;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesExtractor;
import org.checkerframework.checker.nullness.qual.Nullable;

public class StrutsCodeAttributesExtractor extends CodeAttributesExtractor<ActionInvocation, Void> {

  @Override
  protected Class<?> codeClass(ActionInvocation actionInvocation) {
    return actionInvocation.getAction().getClass();
  }

  @Override
  protected String methodName(ActionInvocation actionInvocation) {
    return actionInvocation.getProxy().getMethod();
  }

  @Override
  protected @Nullable String filePath(ActionInvocation actionInvocation) {
    return null;
  }

  @Override
  protected @Nullable Long lineNumber(ActionInvocation actionInvocation) {
    return null;
  }
}
