/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.commons.BytesTransferMetrics.createOrGetWithParentContext;
import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.ApacheHttpClientSingletons.instrumenter;
import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.commons.ApacheHttpClientInternalEntityStorage.getFinalRequest;
import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.commons.ApacheHttpClientInternalEntityStorage.getFinalResponse;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.apachehttpclient.commons.BytesTransferMetrics;
import io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.commons.ApacheHttpClientRequest;
import javax.annotation.Nullable;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;

public final class ApacheHttpClientInstrumentationHelper {
  @Nullable
  public static Context startInstrumentation(
      Context parentContext, HttpRequest request, ApacheHttpClientRequest otelRequest) {
    if (!instrumenter().shouldStart(parentContext, otelRequest)) {
      return null;
    }

    if (request instanceof HttpEntityEnclosingRequest) {
      HttpEntity originalEntity = ((HttpEntityEnclosingRequest) request).getEntity();
      if (originalEntity != null && originalEntity.isChunked()) {
        BytesTransferMetrics metrics = createOrGetWithParentContext(parentContext);
        HttpEntity wrappedHttpEntity = new WrappedHttpEntity(metrics, originalEntity);
        ((HttpEntityEnclosingRequest) request).setEntity(wrappedHttpEntity);
      }
    }

    return instrumenter().start(parentContext, otelRequest);
  }

  public static <T> void endInstrumentation(
      Context context, ApacheHttpClientRequest otelRequest, T result, Throwable throwable) {
    ApacheHttpClientRequest finalRequest = getFinalRequest(otelRequest, context);
    HttpResponse finalResponse = getFinalResponse(result, context);
    instrumenter().end(context, finalRequest, finalResponse, throwable);
  }

  private ApacheHttpClientInstrumentationHelper() {}
}
