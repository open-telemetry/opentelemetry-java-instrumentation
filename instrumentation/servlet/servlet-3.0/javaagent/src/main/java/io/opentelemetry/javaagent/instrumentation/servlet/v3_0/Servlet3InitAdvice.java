/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import static io.opentelemetry.javaagent.instrumentation.servlet.v3_0.Servlet3Singletons.SERVLET_MAPPING_RESOLVER_FACTORY;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class Servlet3InitAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void servletInit(
      @Advice.This Servlet servlet, @Advice.Argument(0) ServletConfig servletConfig) {
    if (servletConfig == null) {
      return;
    }
    SERVLET_MAPPING_RESOLVER_FACTORY.set(
        servlet, new Servlet3MappingResolverFactory(servletConfig));
  }
}
