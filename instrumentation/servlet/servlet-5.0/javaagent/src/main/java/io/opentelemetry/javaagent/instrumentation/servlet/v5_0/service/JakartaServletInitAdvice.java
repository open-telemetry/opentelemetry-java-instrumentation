/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.service;

import static io.opentelemetry.javaagent.instrumentation.servlet.v5_0.Servlet5Singletons.SERVLET_MAPPING_RESOLVER;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class JakartaServletInitAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void servletInit(
      @Advice.This Servlet servlet, @Advice.Argument(0) ServletConfig servletConfig) {
    if (servletConfig == null) {
      return;
    }
    SERVLET_MAPPING_RESOLVER.set(servlet, new JakartaServletMappingResolverFactory(servletConfig));
  }
}
