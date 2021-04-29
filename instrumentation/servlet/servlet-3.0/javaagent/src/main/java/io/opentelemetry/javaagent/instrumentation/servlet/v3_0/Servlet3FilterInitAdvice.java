/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import io.opentelemetry.instrumentation.servlet.v3_0.Servlet3FilterConfigHolder;
import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import net.bytebuddy.asm.Advice;

public class Servlet3FilterInitAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void filterInit(
      @Advice.This Filter filter, @Advice.Argument(0) FilterConfig filterConfig) {
    if (filterConfig == null) {
      return;
    }
    Servlet3FilterConfigHolder.setFilterConfig(filter, filterConfig);
  }
}
