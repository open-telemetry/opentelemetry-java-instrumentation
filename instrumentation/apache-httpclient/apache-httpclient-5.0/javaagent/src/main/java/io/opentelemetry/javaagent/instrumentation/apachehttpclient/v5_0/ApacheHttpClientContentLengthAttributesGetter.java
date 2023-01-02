/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.commons.BytesTransferMetrics.getFromParentContext;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.apachehttpclient.commons.BytesTransferMetrics;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nonnull;
import org.apache.hc.core5.http.HttpResponse;

public class ApacheHttpClientContentLengthAttributesGetter
    implements AttributesExtractor<ApacheHttpClientRequest, HttpResponse> {

  @Override
  public void onStart(
      @Nonnull AttributesBuilder attributes,
      @Nonnull Context parentContext,
      @Nonnull ApacheHttpClientRequest request
  ) {}

  @Override
  public void onEnd(
      @Nonnull AttributesBuilder attributes,
      @Nonnull Context context,
      ApacheHttpClientRequest request,
      HttpResponse response,
      Throwable error) {
    Context parentContext = request.getParentContext();
    BytesTransferMetrics metrics = getFromParentContext(parentContext);
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
