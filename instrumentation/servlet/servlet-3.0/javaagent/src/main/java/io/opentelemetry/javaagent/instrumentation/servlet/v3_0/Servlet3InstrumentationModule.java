/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.servlet.common.async.AsyncContextInstrumentation;
import io.opentelemetry.javaagent.instrumentation.servlet.common.async.AsyncContextStartInstrumentation;
import io.opentelemetry.javaagent.instrumentation.servlet.common.async.AsyncStartInstrumentation;
import io.opentelemetry.javaagent.instrumentation.servlet.common.service.ServletAndFilterInstrumentation;
import io.opentelemetry.javaagent.instrumentation.servlet.javax.response.JavaxResponseInstrumentationFactory;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class Servlet3InstrumentationModule extends InstrumentationModule {
  private static final String BASE_PACKAGE = "javax.servlet";

  public Servlet3InstrumentationModule() {
    super("servlet", "servlet-3.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("javax.servlet.ServletRegistration");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new AsyncContextInstrumentation(BASE_PACKAGE, adviceClassName(".AsyncDispatchAdvice")),
        new AsyncContextStartInstrumentation(
            BASE_PACKAGE, adviceClassName(".Servlet3AsyncContextStartAdvice")),
        new AsyncStartInstrumentation(BASE_PACKAGE, adviceClassName(".Servlet3AsyncStartAdvice")),
        new ServletAndFilterInstrumentation(
            BASE_PACKAGE,
            adviceClassName(".Servlet3Advice"),
            adviceClassName(".Servlet3InitAdvice"),
            adviceClassName(".Servlet3FilterInitAdvice")),
        JavaxResponseInstrumentationFactory.create());
  }

  private static String adviceClassName(String suffix) {
    return Servlet3InstrumentationModule.class.getPackage().getName() + suffix;
  }
}
