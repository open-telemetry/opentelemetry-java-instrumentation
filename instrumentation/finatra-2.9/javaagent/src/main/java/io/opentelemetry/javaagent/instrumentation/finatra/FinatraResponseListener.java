/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finatra;

import static io.opentelemetry.javaagent.instrumentation.finatra.FinatraTracer.tracer;

import com.twitter.finagle.http.Response;
import com.twitter.util.FutureEventListener;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

public class FinatraResponseListener implements FutureEventListener<Response> {
  private final Context context;
  private final Scope scope;

  public FinatraResponseListener(Context context, Scope scope) {
    this.context = context;
    this.scope = scope;
  }

  @Override
  public void onSuccess(Response response) {
    scope.close();
    tracer().end(context);
  }

  @Override
  public void onFailure(Throwable cause) {
    scope.close();
    tracer().endExceptionally(context, cause);
  }
}
