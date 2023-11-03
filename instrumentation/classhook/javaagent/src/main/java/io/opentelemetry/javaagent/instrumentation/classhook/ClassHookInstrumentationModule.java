/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.classhook;

import static java.util.logging.Level.FINE;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

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
  public boolean isHelperClass(String className) {
    logger.log(FINE, "ClassHookInstrumentationModule checking {0}", className);
    return className.startsWith("com.azure.spring.cloud.test.config.client");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    logger.log(FINE, "ClassHookInstrumentationModule ClassHookInstrumentation returned");
    return Collections.singletonList(new ClassHookInstrumentation());
  }
}
