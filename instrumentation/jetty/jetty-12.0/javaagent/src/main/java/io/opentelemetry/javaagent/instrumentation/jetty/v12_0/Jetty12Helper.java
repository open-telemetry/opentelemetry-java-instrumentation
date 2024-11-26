/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v12_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.servlet.AppServerBridge;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletHelper;
import javax.annotation.Nullable;
import org.eclipse.jetty.server.HttpStream;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

public class Jetty12Helper {
  private final Instrumenter<Request, Response> instrumenter;

  Jetty12Helper(Instrumenter<Request, Response> instrumenter) {
    this.instrumenter = instrumenter;
  }

  public boolean shouldStart(Context parentContext, Request request) {
    return instrumenter.shouldStart(parentContext, request);
  }

  public Context start(Context parentContext, Request request, Response response) {
    Context context = instrumenter.start(parentContext, request);
    request.addFailureListener(throwable -> end(context, request, response, throwable));
    // detect request completion
    // https://github.com/jetty/jetty.project/blob/52d94174e2c7a6e794c6377dcf9cd3ed0b9e1806/jetty-core/jetty-server/src/main/java/org/eclipse/jetty/server/handler/EventsHandler.java#L75
    request.addHttpStreamWrapper(
        stream ->
            new HttpStream.Wrapper(stream) {
              @Override
              public void succeeded() {
                end(context, request, response, null);
                super.succeeded();
              }

              @Override
              public void failed(Throwable throwable) {
                end(context, request, response, throwable);
                super.failed(throwable);
              }
            });

    return context;
  }

  public void end(Context context, Request request, Response response, @Nullable Throwable error) {
    if (error == null) {
      error = AppServerBridge.getException(context);
    }
    if (error == null) {
      error = (Throwable) request.getAttribute(ServletHelper.ASYNC_EXCEPTION_ATTRIBUTE);
    }

    instrumenter.end(context, request, response, error);
  }
}
