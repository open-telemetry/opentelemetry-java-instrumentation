/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.glassfish.jersey.client;

import static io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0.JerseyClientUtil.handleException;

import javax.ws.rs.ProcessingException;
import org.glassfish.jersey.process.internal.RequestScope;

// implemented interface is package private so wrapper needs to be in the same package
public class OpenTelemetryResponseCallbackWrapper implements ResponseCallback {
  private final ClientRequest request;
  private final ResponseCallback delegate;

  public OpenTelemetryResponseCallbackWrapper(ClientRequest request, ResponseCallback delegate) {
    this.request = request;
    this.delegate = delegate;
  }

  public static Object wrap(ClientRequest request, Object callback) {
    if (callback instanceof ResponseCallback) {
      return new OpenTelemetryResponseCallbackWrapper(request, (ResponseCallback) callback);
    }
    return callback;
  }

  @Override
  public void completed(ClientResponse clientResponse, RequestScope requestScope) {
    delegate.completed(clientResponse, requestScope);
  }

  @Override
  public void failed(ProcessingException exception) {
    handleException(request, exception.getCause());
    delegate.failed(exception);
  }
}
