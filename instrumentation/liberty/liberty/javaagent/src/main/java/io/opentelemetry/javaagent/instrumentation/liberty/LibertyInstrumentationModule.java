/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

/**
 * Instrumenting request handling in Liberty.
 *
 * <ul>
 *   <li>On entry to WebApp.handleRequest remember request. {@link
 *       LibertyWebAppInstrumentation.HandleRequestAdvice}
 *   <li>On call to WebApp.isForbidden (called from WebApp.handleRequest) start span based on
 *       remembered request. We don't start span immediately at the start or handleRequest because
 *       HttpServletRequest isn't usable yet. {@link LibertyWebAppInstrumentation.IsForbiddenAdvice}
 *   <li>On exit from WebApp.handleRequest close the span. {@link
 *       LibertyWebAppInstrumentation.HandleRequestAdvice}
 * </ul>
 */
@AutoService(InstrumentationModule.class)
public class LibertyInstrumentationModule extends InstrumentationModule {

  public LibertyInstrumentationModule() {
    super("liberty");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new LibertyWebAppInstrumentation());
  }
}
