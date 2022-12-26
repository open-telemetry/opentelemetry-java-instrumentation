/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.ApacheHttpClientSingletons.getBytesTransferMetrics;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.apache.http.HttpResponse;

public class ApacheHttpClientContentLengthAttributesGetter
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
    Context parentContext = request.getParentContext();
    BytesTransferMetrics metrics = getBytesTransferMetrics(parentContext);
    if (metrics != null) {
      Long responseLength = metrics.getResponseContentLength();
      if (responseLength != null) {
        attributes.put(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH, responseLength);
      }
      Long requestLength = metrics.getRequestContentLength();
      if (requestLength != null) {
        attributes.put(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH, requestLength);
      }
    }
  }
}
