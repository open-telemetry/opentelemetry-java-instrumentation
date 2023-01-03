/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.commons.BytesTransferMetrics.createOrGetWithParentContext;
import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0.ApacheHttpClientSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.apachehttpclient.commons.BytesTransferMetrics;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpResponse;

public final class ApacheHttpClientHelper {
  public static Context startInstrumentation(
      Context parentContext, ClassicHttpRequest request, ApacheHttpClientRequest otelRequest) {
    if (!instrumenter().shouldStart(parentContext, otelRequest)) {
      return null;
    }

    HttpEntity originalEntity = request.getEntity();
    if (originalEntity != null) {
      BytesTransferMetrics metrics = createOrGetWithParentContext(parentContext);
      HttpEntity wrappedHttpEntity = new WrappedHttpEntity(metrics, originalEntity);
      request.setEntity(wrappedHttpEntity);
    }

    return instrumenter().start(parentContext, otelRequest);
  }

  public static void endInstrumentation(
      Context context, ApacheHttpClientRequest otelRequest, Object result, Throwable throwable) {
    if (result instanceof CloseableHttpResponse) {
      HttpEntity entity = ((CloseableHttpResponse) result).getEntity();
      if (entity != null) {
        long contentLength = entity.getContentLength();
        Context parentContext = otelRequest.getParentContext();
        BytesTransferMetrics metrics = createOrGetWithParentContext(parentContext);
        metrics.setResponseContentLength(contentLength);
      }
    }
    HttpResponse httpResponse = null;
    if (result instanceof HttpResponse) {
      httpResponse = (HttpResponse) result;
    }
    instrumenter().end(context, otelRequest, httpResponse, throwable);
  }

  private ApacheHttpClientHelper() {}
}
