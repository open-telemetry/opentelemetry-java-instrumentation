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
    Context parentContext = request.getParentContext();
    ApacheContentLengthMetrics metrics = getContentLengthMetrics(parentContext);
    if (metrics != null) {
      // response-length indicates bytes read by the stream even when content-length header is not
      // present (for example: chunked encoding). Note that we can't calculate this metric for
      // non async client, because we don't want to consume the stream unnecessarily, in case of
      // async client, the stream is anyways consumed even if the caller doesn't intend to use it
      long responseBytes = metrics.getResponseBytes();
      if (responseBytes != 0L) {
        attributes.put(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH, responseBytes);
      }
      // request-length indicates bytes written to the stream even when content-length header is not
      // present (for example: chunked encoding).
      long requestBytes = metrics.getRequestBytes();
      if (requestBytes != 0L) {
        attributes.put(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH, requestBytes);
      }
    }
  }
}
