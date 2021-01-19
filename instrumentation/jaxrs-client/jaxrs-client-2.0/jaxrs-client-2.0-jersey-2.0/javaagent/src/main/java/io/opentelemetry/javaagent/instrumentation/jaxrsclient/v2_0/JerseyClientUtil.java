/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0.JaxRsClientTracer.tracer;

import io.opentelemetry.context.Context;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import org.glassfish.jersey.client.ClientRequest;

public final class JerseyClientUtil {

  public static Future<?> addErrorReporting(ClientRequest context, Future<?> future) {
    // since jersey 2.30 jersey internally uses CompletableFuture
    // we can't wrap it with WrappedFuture as it causes ClassCastException when casting
    // to CompletableFuture
    if (future instanceof CompletableFuture) {
      future =
          ((CompletableFuture<?>) future)
              .whenComplete(
                  (result, exception) -> {
                    if (exception != null) {
                      handleException(context, exception);
                    }
                  });
    } else {
      if (!(future instanceof WrappedFuture)) {
        future = new WrappedFuture<>(future, context);
      }
    }

    return future;
  }

  public static void handleException(ClientRequest context, Throwable exception) {
    Object prop = context.getProperty(ClientTracingFilter.CONTEXT_PROPERTY_NAME);
    if (prop instanceof Context) {
      tracer().endExceptionally((Context) prop, exception);
    }
  }

  private JerseyClientUtil() {}
}
