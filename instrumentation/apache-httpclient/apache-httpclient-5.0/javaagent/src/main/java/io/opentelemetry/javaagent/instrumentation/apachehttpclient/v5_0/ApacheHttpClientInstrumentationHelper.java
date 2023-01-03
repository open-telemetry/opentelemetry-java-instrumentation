/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.commons.BytesTransferMetrics.createOrGetWithParentContext;
import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0.ApacheHttpClientInternalEntityStorage.getFinalRequest;
import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0.ApacheHttpClientInternalEntityStorage.getFinalResponse;
import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0.ApacheHttpClientSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.apachehttpclient.commons.BytesTransferMetrics;
import javax.annotation.Nullable;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpEntityContainer;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;

public final class ApacheHttpClientInstrumentationHelper {
  @Nullable
  public static Context startInstrumentation(
      Context parentContext, HttpRequest request, ApacheHttpClientRequest otelRequest) {
    if (!instrumenter().shouldStart(parentContext, otelRequest)) {
      return null;
    }

    if (request instanceof HttpEntityContainer) {
      HttpEntity originalEntity = ((HttpEntityContainer) request).getEntity();
      if (originalEntity != null && originalEntity.isChunked()) {
        BytesTransferMetrics metrics = createOrGetWithParentContext(parentContext);
        HttpEntity wrappedHttpEntity = new WrappedHttpEntity(metrics, originalEntity);
        ((HttpEntityContainer) request).setEntity(wrappedHttpEntity);
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
