/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.service;

import io.opentelemetry.instrumentation.api.servlet.MappingResolver;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
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
    InstrumentationContext.get(Filter.class, MappingResolver.Factory.class)
        .putIfAbsent(filter, new JakartaServletFilterMappingResolverFactory(filterConfig));
  }
}
