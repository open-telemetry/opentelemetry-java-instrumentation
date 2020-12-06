/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import static io.opentelemetry.javaagent.instrumentation.httpurlconnection.HttpUrlConnectionTracer.tracer;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.DefaultHttpClientOperation;

public class DefaultHttpUrlConnectionOperation extends DefaultHttpClientOperation<HttpUrlResponse>
    implements HttpUrlConnectionOperation {

  public boolean finished;

  public DefaultHttpUrlConnectionOperation(Context context, Context parentContext) {
    super(context, parentContext, tracer());
  }

  @Override
  public void end(HttpUrlResponse response) {
    super.end(response);
    finished = true;
  }

  @Override
  public void endExceptionally(Throwable throwable) {
    super.endExceptionally(throwable);
    finished = true;
  }

  @Override
  public boolean finished() {
    return finished;
  }
}
