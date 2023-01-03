/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.commons;

import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.commons.ApacheHttpClientAttributesHelper.getFirstHeader;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.apachehttpclient.commons.BytesTransferMetrics;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nonnull;
import org.apache.http.HttpResponse;

public final class ApacheHttpClientContentLengthAttributesGetter
    implements AttributesExtractor<ApacheHttpClientRequest, HttpResponse> {
  private static final String CONTENT_LENGTH_HEADER = "content-length";

  @Override
  public void onStart(
      @Nonnull AttributesBuilder attributes,
      @Nonnull Context parentContext,
      @Nonnull ApacheHttpClientRequest otelRequest) {}

  @Override
  public void onEnd(
      @Nonnull AttributesBuilder attributes,
      @Nonnull Context context,
      @Nonnull ApacheHttpClientRequest otelRequest,
      HttpResponse response,
      Throwable error) {
    BytesTransferMetrics metrics = otelRequest.getBytesTransferMetrics();
    if (metrics != null) {
      Long requestContentLength = getContentLength(otelRequest, metrics);
      if (requestContentLength != null) {
        attributes.put(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH, requestContentLength);
      }

      Long responseContentLength = getContentLength(response, metrics);
      if (responseContentLength != null) {
        attributes.put(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH, responseContentLength);
      }
    }
  }

  private static Long getContentLength(
      ApacheHttpClientRequest request, BytesTransferMetrics metrics) {
    String requestContentLength = request.getFirstHeader(CONTENT_LENGTH_HEADER);
    if (requestContentLength != null) {
      return null;
    }
    return metrics.getRequestContentLength();
  }

  private static Long getContentLength(HttpResponse response, BytesTransferMetrics metrics) {
    if (response == null) {
      return null;
    }
    String responseContentLength = getFirstHeader(response, CONTENT_LENGTH_HEADER);
    if (responseContentLength != null) {
      return null;
    }
    return metrics.getResponseContentLength();
  }
}
