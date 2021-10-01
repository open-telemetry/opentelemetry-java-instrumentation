/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.instrumentation.api.servlet.MappingResolver;
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
    VirtualField.find(Servlet.class, MappingResolver.Factory.class)
        .setIfNull(servlet, new Servlet3MappingResolverFactory(servletConfig));
  }
}
