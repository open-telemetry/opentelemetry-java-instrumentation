/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.javax;

import io.opentelemetry.instrumentation.servlet.javax.JavaxServletAccessor;
import javax.servlet.http.HttpServletRequest;

public class JavaxCommonServletAccessor extends JavaxServletAccessor<Void> {
  public static final JavaxCommonServletAccessor INSTANCE = new JavaxCommonServletAccessor();

  private JavaxCommonServletAccessor() {}

  @Override
  public Integer getRequestRemotePort(HttpServletRequest httpServletRequest) {
    return null;
  }

  @Override
  public int getResponseStatus(Void unused) {
    return 0;
  }

  @Override
  public boolean isResponseCommitted(Void unused) {
    return false;
  }
}
