/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import io.opentelemetry.instrumentation.servlet.naming.MappingResolverFactory;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class Servlet3FilterInitAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void filterInit(
      @Advice.This Filter filter, @Advice.Argument(0) FilterConfig filterConfig) {
    if (filterConfig == null) {
      return;
    }
    InstrumentationContext.get(Filter.class, MappingResolverFactory.class)
        .putIfAbsent(filter, new Servlet3FilterMappingResolverFactory(filterConfig));
  }
}
