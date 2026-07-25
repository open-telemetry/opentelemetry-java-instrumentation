/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thread.internal;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ComponentProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;

/**
 * Declarative configuration component provider that exposes {@link ThreadDetailsSpanProcessor}
 * under the name {@value #NAME}.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class ThreadDetailsComponentProvider implements ComponentProvider {

  public static final String NAME = "thread_details";

  @Override
  public Class<SpanProcessor> getType() {
    return SpanProcessor.class;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public SpanProcessor create(DeclarativeConfigProperties config) {
    return new ThreadDetailsSpanProcessor();
  }
}
