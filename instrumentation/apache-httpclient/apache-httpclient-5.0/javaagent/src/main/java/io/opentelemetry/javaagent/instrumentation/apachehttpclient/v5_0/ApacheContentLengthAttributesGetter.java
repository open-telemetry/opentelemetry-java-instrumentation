package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

public class ApacheContentLengthAttributesGetter implements
    AttributesExtractor<ApacheHttpRequest, ApacheHttpResponse> {
  private static final VirtualField<Context, ApacheContentLengthMetrics> virtualField =
      VirtualField.find(Context.class, ApacheContentLengthMetrics.class);

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext,
      ApacheHttpRequest request) {
  }

  @Override
  public void onEnd(AttributesBuilder attributes, Context context,
      ApacheHttpRequest request, ApacheHttpResponse response,
      Throwable error) {
    Context parentContext = request.getParentContext();
    ApacheContentLengthMetrics metrics = virtualField.get(parentContext);
    if (metrics != null) {
      long responseBytes = metrics.getResponseBytes();
      if (responseBytes != 0L) {
        attributes.put(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH, responseBytes);
      }
      long requestBytes = metrics.getRequestBytes();
      if (requestBytes != 0L) {
        attributes.put(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH, requestBytes);
      }
    }
  }
}
