/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webmvc;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import org.springframework.web.servlet.ModelAndView;

public final class SpringWebMvcInstrumenterFactory {

  private final String instrumentationName;

  public SpringWebMvcInstrumenterFactory(String instrumentationName) {
    this.instrumentationName = instrumentationName;
  }

  public Instrumenter<Object, Void> createHandlerInstrumenter() {
    HandlerCodeAttributesGetter codeAttributesGetter = new HandlerCodeAttributesGetter();
    return Instrumenter.<Object, Void>builder(
            GlobalOpenTelemetry.get(),
            instrumentationName,
            CodeSpanNameExtractor.create(codeAttributesGetter))
        .addAttributesExtractor(CodeAttributesExtractor.create(codeAttributesGetter))
        .setEnabled(ExperimentalConfig.get().controllerTelemetryEnabled())
        .buildInstrumenter();
  }

  public Instrumenter<ModelAndView, Void> createModelAndViewInstrumenter() {
    return Instrumenter.<ModelAndView, Void>builder(
            GlobalOpenTelemetry.get(), instrumentationName, new ModelAndViewSpanNameExtractor())
        .addAttributesExtractor(new ModelAndViewAttributesExtractor())
        .setEnabled(ExperimentalConfig.get().viewTelemetryEnabled())
        .buildInstrumenter();
  }
}
