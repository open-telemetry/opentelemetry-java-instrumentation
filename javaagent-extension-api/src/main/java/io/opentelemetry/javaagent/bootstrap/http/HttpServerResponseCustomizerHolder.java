/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.http;

import io.opentelemetry.context.Context;
import javax.annotation.Nonnull;

/**
 * Holds the currently active response customizer. This is set during agent initialization to an
 * instance that calls each {@link HttpServerResponseCustomizer} found in the agent classpath. It is
 * intended to be used directly from HTTP server library instrumentations, which is why this package
 * is inside the bootstrap package that gets loaded in the bootstrap classloader.
 */
public class HttpServerResponseCustomizerHolder {
  @Nonnull
  private static volatile HttpServerResponseCustomizer responseCustomizer = new NoOpCustomizer();

  public static void setCustomizer(@Nonnull HttpServerResponseCustomizer customizer) {
    HttpServerResponseCustomizerHolder.responseCustomizer = customizer;
  }

  @Nonnull
  public static HttpServerResponseCustomizer getCustomizer() {
    return responseCustomizer;
  }

  private HttpServerResponseCustomizerHolder() {}

  private static class NoOpCustomizer implements HttpServerResponseCustomizer {

    @Override
    public <T> void onStart(
        Context serverContext, T response, HttpServerResponseMutator<T> responseMutator) {}
  }
}
