/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0.ApacheHttpClientSingletons.getContentLengthMetrics;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.apache.hc.core5.http.HttpResponse;

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
    BytesTransferMetrics metrics = getContentLengthMetrics(parentContext);
    if (metrics != null) {
      // response-length indicates bytes read by the stream, when content-length is present via
      // header we use that directly, else the value is computed for the bytes read in
      // even when content-length header is not
      // present (for example: chunked encoding). Note that we can't calculate this metric for
      // non async client, because the input stream is lazily consumed, while for async client,
      // the stream is eagerly consumed and hence it is possible to get the metric.
      Long responseLength = metrics.getResponseContentLength();
      if (responseLength != null) {
        attributes.put(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH, responseLength);
      }
      // request-length indicates bytes written to the stream even when content-length header is not
      // present (for example: chunked encoding).
      Long requestLength = metrics.getRequestContentLength();
      if (requestLength != null) {
        attributes.put(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH, requestLength);
      }
    }
  }
}
