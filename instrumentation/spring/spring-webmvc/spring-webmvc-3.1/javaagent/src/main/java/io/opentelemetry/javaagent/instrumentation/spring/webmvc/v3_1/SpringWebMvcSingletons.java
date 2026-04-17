/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webmvc.v3_1;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.spring.webmvc.SpringWebMvcInstrumenterFactory;
import org.springframework.web.servlet.ModelAndView;

public class SpringWebMvcSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-webmvc-3.1";

  private static final Instrumenter<Object, Void> handlerInstrumenter;

  private static final Instrumenter<ModelAndView, Void> modelAndViewInstrumenter;

  static {
    SpringWebMvcInstrumenterFactory factory =
        new SpringWebMvcInstrumenterFactory(INSTRUMENTATION_NAME);
    handlerInstrumenter = factory.createHandlerInstrumenter();
    modelAndViewInstrumenter = factory.createModelAndViewInstrumenter();
  }

  public static Instrumenter<Object, Void> handlerInstrumenter() {
    return handlerInstrumenter;
  }

  public static Instrumenter<ModelAndView, Void> modelAndViewInstrumenter() {
    return modelAndViewInstrumenter;
  }

  private SpringWebMvcSingletons() {}
}
