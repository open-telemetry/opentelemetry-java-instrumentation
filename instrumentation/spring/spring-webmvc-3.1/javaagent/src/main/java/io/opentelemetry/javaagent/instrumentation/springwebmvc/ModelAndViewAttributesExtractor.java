/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springwebmvc;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.ClassNames;
import javax.annotation.Nullable;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;

public class ModelAndViewAttributesExtractor implements AttributesExtractor<ModelAndView, Void> {

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      Config.get()
          .getBoolean("otel.instrumentation.spring-webmvc.experimental-span-attributes", false);

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, ModelAndView modelAndView) {
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      attributes.put("spring-webmvc.view.name", modelAndView.getViewName());
      View view = modelAndView.getView();
      if (view != null) {
        attributes.put("spring-webmvc.view.type", ClassNames.simpleName(view.getClass()));
      }
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ModelAndView modelAndView,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
