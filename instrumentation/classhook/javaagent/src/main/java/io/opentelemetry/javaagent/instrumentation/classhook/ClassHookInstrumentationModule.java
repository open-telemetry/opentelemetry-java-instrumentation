/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.classhook;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.logging.Level.FINE;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class ClassHookInstrumentationModule extends InstrumentationModule {

  private static final Logger logger =
      Logger.getLogger(ClassHookInstrumentationModule.class.getName());

  public ClassHookInstrumentationModule() {
    super("class-hook", "class-hook-0.1");
    logger.log(FINE, "ClassHookInstrumentationModule initialized");
  }

  @Override
  public int order() {
    return 100;
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("com.tests.springboot.controller.WebController");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    logger.log(FINE, "ClassHookInstrumentationModule ClassHookInstrumentation returned");
    return Collections.singletonList(new ClassHookInstrumentation());
  }
}
