/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;

/**
 * Customizer of the {@link Context}. Instrumented libraries will call {@link #start(Context,
 * Object, Attributes)} during {@link Instrumenter#start(Context, Object)}, allowing customization
 * of the {@link Context} that is returned from that method.
 */
public interface ContextCustomizer<REQUEST> {

  /**
   * Context customizer method that is called during {@link Instrumenter#start(Context, Object)},
   * allowing customization * of the {@link Context} that is returned from that method.
   */
  // TODO (trask) should we pass startNanos
  //  similar to https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/4155?
  Context start(Context context, REQUEST request, Attributes startAttributes);
}
