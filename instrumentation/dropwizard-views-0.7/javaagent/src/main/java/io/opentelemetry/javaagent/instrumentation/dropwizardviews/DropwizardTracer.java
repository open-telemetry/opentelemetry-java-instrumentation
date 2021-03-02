/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.dropwizardviews;

import io.opentelemetry.instrumentation.api.tracer.BaseTracer;

public class DropwizardTracer extends BaseTracer {
  private static final DropwizardTracer TRACER = new DropwizardTracer();

  public static DropwizardTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.dropwizard-views-0.7";
  }
}
