/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springwebmvc;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.tracer.ClassNames;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;

public class ModelAndViewAttributesExtractor extends AttributesExtractor<ModelAndView, Void> {

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      Config.get()
          .getBoolean("otel.instrumentation.spring-webmvc.experimental-span-attributes", false);

  @Override
  protected void onStart(AttributesBuilder attributes, ModelAndView modelAndView) {
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      attributes.put("spring-webmvc.view.name", modelAndView.getViewName());
      View view = modelAndView.getView();
      if (view != null) {
        attributes.put("spring-webmvc.view.type", ClassNames.simpleName(view.getClass()));
      }
    }
  }

  @Override
  protected void onEnd(
      AttributesBuilder attributes, ModelAndView modelAndView, @Nullable Void unused) {}
}
