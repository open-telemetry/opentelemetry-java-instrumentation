/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0.copied;

import io.opentelemetry.context.Context;

/**
 * Holds the currently active response customizer. This is set during agent initialization to an
 * instance that calls each {@link HttpServerResponseCustomizer} found in the agent classpath. It is
 * intended to be used directly from HTTP server library instrumentations, which is why this package
 * is inside the bootstrap package that gets loaded in the bootstrap classloader.
 */
public final class HttpServerResponseCustomizerHolder {
  private static volatile HttpServerResponseCustomizer responseCustomizer = new NoOpCustomizer();

  public static void setCustomizer(HttpServerResponseCustomizer customizer) {
    HttpServerResponseCustomizerHolder.responseCustomizer = customizer;
  }

  public static HttpServerResponseCustomizer getCustomizer() {
    return responseCustomizer;
  }

  private HttpServerResponseCustomizerHolder() {}

  private static class NoOpCustomizer implements HttpServerResponseCustomizer {

    @Override
    public <T> void customize(
        Context serverContext, T response, HttpServerResponseMutator<T> responseMutator) {}
  }
}
