/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet;

import static java.util.Collections.singletonMap;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.instrumentation.servlet.async.AsyncContextInstrumentation;
import io.opentelemetry.javaagent.instrumentation.servlet.dispatcher.RequestDispatcherInstrumentation;
import io.opentelemetry.javaagent.instrumentation.servlet.response.HttpServletResponseInstrumentation;
import io.opentelemetry.javaagent.instrumentation.servlet.service.ServletAndFilterInstrumentation;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@AutoService(InstrumentationModule.class)
public class JakartaServletInstrumentationModule extends InstrumentationModule {
  private static final String BASE_PACKAGE = "jakarta.servlet";

  public JakartaServletInstrumentationModule() {
    super("servlet", "servlet-jakarta");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Arrays.asList(
        new AsyncContextInstrumentation(
            BASE_PACKAGE, adviceClassName(".async.AsyncDispatchAdvice")),
        new ServletAndFilterInstrumentation(
            BASE_PACKAGE, adviceClassName(".service.JakartaServletServiceAdvice")),
        new HttpServletResponseInstrumentation(
            BASE_PACKAGE, adviceClassName(".response.ResponseSendAdvice")),
        new RequestDispatcherInstrumentation(
            BASE_PACKAGE, adviceClassName(".dispatcher.RequestDispatcherAdvice")));
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap(BASE_PACKAGE + ".RequestDispatcher", String.class.getName());
  }

  private static String adviceClassName(String suffix) {
    return JakartaServletInstrumentationModule.class.getPackage().getName() + suffix;
  }
}
