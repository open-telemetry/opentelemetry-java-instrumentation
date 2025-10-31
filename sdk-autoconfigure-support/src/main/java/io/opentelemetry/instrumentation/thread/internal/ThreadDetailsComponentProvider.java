/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thread.internal;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ComponentProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class ThreadDetailsComponentProvider implements ComponentProvider<SpanProcessor> {
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
