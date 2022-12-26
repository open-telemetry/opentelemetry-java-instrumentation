/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.ApacheHttpClientSingletons.createOrGetBytesTransferMetrics;
import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.ApacheHttpClientSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import net.bytebuddy.asm.Advice;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;

@SuppressWarnings("unused")
public class RequestAdvice {
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void methodEnter(
      @Advice.Argument(0) HttpHost host,
      @Advice.Argument(1) HttpRequest request,
      @Advice.Argument(2) HttpContext httpContext,
      @Advice.Local("otelRequest") ApacheHttpClientRequest otelRequest,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {
    Context parentContext = currentContext();

    otelRequest = new ApacheHttpClientRequest(parentContext, host, request);
    if (request instanceof HttpEntityEnclosingRequest) {
      HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
      if (entity != null) {
        long contentLength = entity.getContentLength();
        BytesTransferMetrics metrics = createOrGetBytesTransferMetrics(parentContext);
        metrics.setRequestContentLength(contentLength);
        HttpEntity wrappedHttpEntity = new WrappedHttpEntity(parentContext, entity);
        ((HttpEntityEnclosingRequest) request).setEntity(wrappedHttpEntity);
      }
    }

    if (instrumenter().shouldStart(parentContext, otelRequest)) {
      context = instrumenter().start(parentContext, otelRequest);
      scope = context.makeCurrent();
    }
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void methodExit(
      @Advice.Return HttpResponse response,
      @Advice.Thrown Throwable throwable,
      @Advice.Local("otelRequest") ApacheHttpClientRequest otelRequest,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {
    if (scope != null) {
      scope.close();
      if (response != null) {
        HttpEntity entity = response.getEntity();
        if (entity != null) {
          long contentLength = entity.getContentLength();
          Context parentContext = otelRequest.getParentContext();
          BytesTransferMetrics metrics = createOrGetBytesTransferMetrics(parentContext);
          metrics.setResponseContentLength(contentLength);
        }
      }
      if (throwable != null) {
        instrumenter().end(context, otelRequest, null, throwable);
      } else {
        instrumenter().end(context, otelRequest, response, null);
      }
    }
  }
}
