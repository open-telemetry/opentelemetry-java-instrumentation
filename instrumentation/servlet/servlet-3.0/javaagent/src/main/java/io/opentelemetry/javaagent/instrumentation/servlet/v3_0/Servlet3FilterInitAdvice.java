/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.instrumentation.api.servlet.MappingResolver;
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
    VirtualField.find(Filter.class, MappingResolver.Factory.class)
        .set(filter, new Servlet3FilterMappingResolverFactory(filterConfig));
  }
}
