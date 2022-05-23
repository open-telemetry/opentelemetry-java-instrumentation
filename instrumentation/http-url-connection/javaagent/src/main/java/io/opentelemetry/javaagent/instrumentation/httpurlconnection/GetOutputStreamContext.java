/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import static io.opentelemetry.context.ContextKey.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ImplicitContextKeyed;
import java.net.HttpURLConnection;

public class GetOutputStreamContext implements ImplicitContextKeyed {
  private static final ContextKey<GetOutputStreamContext> KEY =
      named("opentelemetry-http-url-connection-get-output-stream");

  private boolean outputStreamMethodOfSunConnectionCalled;

  private GetOutputStreamContext() {}

  @Override
  public Context storeInContext(Context context) {
    return context.with(KEY, this);
  }

  public static Context init(Context context) {
    if (context.get(KEY) != null) {
      return context;
    }
    return context.with(new GetOutputStreamContext());
  }

  public static GetOutputStreamContext retrieveFrom(Context context) {
    return context.get(KEY);
  }

  public void set(
      Context context,
      Class<? extends HttpURLConnection> connectionClass,
      String methodName,
      String requestMethod) {
    GetOutputStreamContext getOutputStreamContext = context.get(KEY);
    String connectionClassName = connectionClass.getName();
    if ("sun.net.www.protocol.http.HttpURLConnection".equals(connectionClassName)
        && "getOutputStream".equals(methodName)
        && "POST"
            .equals(
                requestMethod) // To be sure that getOutputStream has transformed GET into POST if
    // the method raised an exception
    ) {
      getOutputStreamContext.outputStreamMethodOfSunConnectionCalled = true;
    }
  }

  public boolean isOutputStreamMethodOfSunConnectionCalled() {
    return outputStreamMethodOfSunConnectionCalled;
  }
}
