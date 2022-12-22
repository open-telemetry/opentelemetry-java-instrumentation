package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.apache.http.HttpResponse;

public class ApacheContentLengthAttributesGetter
    implements AttributesExtractor<ApacheHttpClientRequest, HttpResponse> {

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, ApacheHttpClientRequest request) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ApacheHttpClientRequest request,
      HttpResponse response,
      Throwable error) {
    Long requestBytes = request.getRequestBytes();
    if (requestBytes != null) {
      // request-length indicates bytes written to the stream even when content-length header is not
      // present (for example: chunked encoding).
      attributes.put(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH, requestBytes);
    }
  }
}
