/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webmvc.v6_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.spring.webmvc.SpringWebMvcInstrumenterFactory;
import org.springframework.web.servlet.ModelAndView;

public final class SpringWebMvcSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-webmvc-6.0";

  private static final Instrumenter<Object, Void> HANDLER_INSTRUMENTER;

  private static final Instrumenter<ModelAndView, Void> MODEL_AND_VIEW_INSTRUMENTER;

  static {
    SpringWebMvcInstrumenterFactory factory =
        new SpringWebMvcInstrumenterFactory(INSTRUMENTATION_NAME);
    HANDLER_INSTRUMENTER = factory.createHandlerInstrumenter();
    MODEL_AND_VIEW_INSTRUMENTER = factory.createModelAndViewInstrumenter();
  }

  public static Instrumenter<Object, Void> handlerInstrumenter() {
    return HANDLER_INSTRUMENTER;
  }

  public static Instrumenter<ModelAndView, Void> modelAndViewInstrumenter() {
    return MODEL_AND_VIEW_INSTRUMENTER;
  }

  private SpringWebMvcSingletons() {}
}
