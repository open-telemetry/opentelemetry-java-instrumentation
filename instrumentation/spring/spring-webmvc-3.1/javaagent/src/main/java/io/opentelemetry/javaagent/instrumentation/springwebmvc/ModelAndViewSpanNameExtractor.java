/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springwebmvc;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;

public class ModelAndViewSpanNameExtractor implements SpanNameExtractor<ModelAndView> {
  @Override
  public String extract(ModelAndView modelAndView) {
    String viewName = modelAndView.getViewName();
    if (viewName != null) {
      return "Render " + viewName;
    }
    View view = modelAndView.getView();
    if (view != null) {
      return "Render " + view.getClass().getSimpleName();
    }
    // either viewName or view should be non-null, but just in case
    return "Render <unknown>";
  }
}
