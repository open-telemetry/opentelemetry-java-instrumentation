/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.thread.AddThreadDetailsSpanProcessor;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ComponentProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;

@SuppressWarnings("rawtypes")
@AutoService(ComponentProvider.class)
public class AgentTracerComponentProvider implements ComponentProvider<SpanProcessor> {
  @Override
  public String getName() {
    return "thread_details";
  }

  @Override
  public SpanProcessor create(DeclarativeConfigProperties config) {
    return new AddThreadDetailsSpanProcessor();
  }

  @Override
  public Class<SpanProcessor> getType() {
    return SpanProcessor.class;
  }
}
