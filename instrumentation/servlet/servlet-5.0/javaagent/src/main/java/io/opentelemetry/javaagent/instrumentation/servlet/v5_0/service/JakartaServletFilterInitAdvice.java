/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.service;

import static io.opentelemetry.javaagent.instrumentation.servlet.v5_0.Servlet5Singletons.FILTER_MAPPING_RESOLVER;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterConfig;
import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class JakartaServletFilterInitAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void filterInit(
      @Advice.This Filter filter, @Advice.Argument(0) FilterConfig filterConfig) {
    if (filterConfig == null) {
      return;
    }
    FILTER_MAPPING_RESOLVER.set(
        filter, new JakartaServletFilterMappingResolverFactory(filterConfig));
  }
}
