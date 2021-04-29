/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.service;

import io.opentelemetry.instrumentation.servlet.jakarta.v5_0.JakartaServletFilterConfigHolder;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterConfig;
import net.bytebuddy.asm.Advice;

public class JakartaServletFilterInitAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void filterInit(
      @Advice.This Filter filter, @Advice.Argument(0) FilterConfig filterConfig) {
    if (filterConfig == null) {
      return;
    }
    JakartaServletFilterConfigHolder.setFilterConfig(filter, filterConfig);
  }
}
