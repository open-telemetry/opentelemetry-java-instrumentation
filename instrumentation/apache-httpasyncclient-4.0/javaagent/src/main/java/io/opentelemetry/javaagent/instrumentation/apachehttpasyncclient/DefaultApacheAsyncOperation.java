/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient;

import static io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient.ApacheHttpAsyncClientTracer.tracer;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.DefaultHttpClientOperation;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;

public class DefaultApacheAsyncOperation extends DefaultHttpClientOperation<HttpResponse>
    implements ApacheAsyncOperation {

  public DefaultApacheAsyncOperation(Context context, Context parentContext) {
    super(context, parentContext, tracer());
  }

  @Override
  public void inject(HttpRequest request) {
    OpenTelemetry.getGlobalPropagators()
        .getTextMapPropagator()
        .inject(getContext(), request, tracer().getSetter());
  }
}
