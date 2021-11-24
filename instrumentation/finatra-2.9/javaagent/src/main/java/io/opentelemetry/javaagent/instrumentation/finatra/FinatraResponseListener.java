/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finatra;

import static io.opentelemetry.javaagent.instrumentation.finatra.FinatraSingletons.instrumenter;

import com.twitter.finagle.http.Response;
import com.twitter.util.FutureEventListener;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.field.VirtualField;

public final class FinatraResponseListener implements FutureEventListener<Response> {

  private static final VirtualField<Response, Throwable> responseThrowableField =
      VirtualField.find(Response.class, Throwable.class);

  private final Context context;
  private final Class<?> request;

  public FinatraResponseListener(Context context, Class<?> request) {
    this.context = context;
    this.request = request;
  }

  @Override
  public void onSuccess(Response response) {
    Throwable throwable = responseThrowableField.get(response);
    instrumenter().end(context, request, null, throwable);
  }

  @Override
  public void onFailure(Throwable cause) {
    instrumenter().end(context, request, null, cause);
  }
}
