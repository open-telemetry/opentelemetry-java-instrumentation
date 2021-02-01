/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.wicket;

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.hasClassesNamed;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Arrays;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class WicketInstrumentationModule extends InstrumentationModule {

  public WicketInstrumentationModule() {
    super("wicket", "wicket-8.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // missing before 8.0
    return hasClassesNamed("org.apache.wicket.request.RequestHandlerExecutor");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Arrays.asList(
        new RequestHandlerExecutorInstrumentation(), new DefaultExceptionMapperInstrumentation());
  }
}
