/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.commons.BytesTransferMetrics.createOrGetWithParentContext;
import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.ApacheHttpClientSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.apachehttpclient.commons.BytesTransferMetrics;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;

public final class ApacheHttpClientHelper {

  public static void doOnMethodEnter(Context parentContext, HttpRequest request) {
    if (request instanceof HttpEntityEnclosingRequest) {
      HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
      if (entity != null) {
        long contentLength = entity.getContentLength();
        BytesTransferMetrics metrics = createOrGetWithParentContext(parentContext);
        metrics.setRequestContentLength(contentLength);
        HttpEntity wrappedHttpEntity = new WrappedHttpEntity(parentContext, entity);
        ((HttpEntityEnclosingRequest) request).setEntity(wrappedHttpEntity);
      }
    }
  }

  public static void endInstrumentation(
      Context context, ApacheHttpClientRequest request, Object response, Throwable throwable) {
    HttpResponse httpResponse = null;
    if (response instanceof HttpResponse) {
      httpResponse = (HttpResponse) response;
      HttpEntity entity = httpResponse.getEntity();
      if (entity != null) {
        long contentLength = entity.getContentLength();
        Context parentContext = request.getParentContext();
        BytesTransferMetrics metrics = createOrGetWithParentContext(parentContext);
        metrics.setResponseContentLength(contentLength);
      }
    }
    instrumenter().end(context, request, httpResponse, throwable);
  }

  private ApacheHttpClientHelper() {}
}
