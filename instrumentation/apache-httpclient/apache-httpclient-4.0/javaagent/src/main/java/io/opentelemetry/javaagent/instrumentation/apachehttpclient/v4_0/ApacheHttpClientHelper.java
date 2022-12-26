package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.ApacheHttpClientSingletons.createOrGetBytesTransferMetrics;
import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.ApacheHttpClientSingletons.instrumenter;

import io.opentelemetry.context.Context;
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
        BytesTransferMetrics metrics = createOrGetBytesTransferMetrics(parentContext);
        metrics.setRequestContentLength(contentLength);
        HttpEntity wrappedHttpEntity = new WrappedHttpEntity(parentContext, entity);
        ((HttpEntityEnclosingRequest) request).setEntity(wrappedHttpEntity);
      }
    }
  }

  public static void doMethodExit(
      Context context, ApacheHttpClientRequest request, Object response, Throwable throwable) {
    if (response instanceof HttpResponse) {
      HttpEntity entity = ((HttpResponse) response).getEntity();
      if (entity != null) {
        long contentLength = entity.getContentLength();
        Context parentContext = request.getParentContext();
        BytesTransferMetrics metrics = createOrGetBytesTransferMetrics(parentContext);
        metrics.setResponseContentLength(contentLength);
      }
    }
    if (throwable != null) {
      instrumenter().end(context, request, null, throwable);
    } else if (response instanceof HttpResponse) {
      instrumenter().end(context, request, (HttpResponse) response, null);
    } else {
      // ended in WrappingStatusSettingResponseHandler
    }
  }

  private ApacheHttpClientHelper() {}
}