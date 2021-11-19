/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.servlet.common.async.AsyncContextInstrumentation;
import io.opentelemetry.javaagent.instrumentation.servlet.common.async.AsyncContextStartInstrumentation;
import io.opentelemetry.javaagent.instrumentation.servlet.common.async.AsyncStartInstrumentation;
import io.opentelemetry.javaagent.instrumentation.servlet.common.response.HttpServletResponseInstrumentation;
import io.opentelemetry.javaagent.instrumentation.servlet.common.service.ServletAndFilterInstrumentation;
import java.util.Arrays;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class JakartaServletInstrumentationModule extends InstrumentationModule {
  private static final String BASE_PACKAGE = "jakarta.servlet";

  public JakartaServletInstrumentationModule() {
    super("servlet", "servlet-5.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Arrays.asList(
        new AsyncContextInstrumentation(
            BASE_PACKAGE, adviceClassName(".async.AsyncDispatchAdvice")),
        new AsyncContextStartInstrumentation(
            BASE_PACKAGE, adviceClassName(".async.AsyncContextStartAdvice")),
        new AsyncStartInstrumentation(BASE_PACKAGE, adviceClassName(".async.AsyncStartAdvice")),
        new ServletAndFilterInstrumentation(
            BASE_PACKAGE,
            adviceClassName(".service.JakartaServletServiceAdvice"),
            adviceClassName(".service.JakartaServletInitAdvice"),
            adviceClassName(".service.JakartaServletFilterInitAdvice")),
        new HttpServletResponseInstrumentation(
            BASE_PACKAGE, adviceClassName(".response.ResponseSendAdvice")));
  }

  private static String adviceClassName(String suffix) {
    return JakartaServletInstrumentationModule.class.getPackage().getName() + suffix;
  }
}
