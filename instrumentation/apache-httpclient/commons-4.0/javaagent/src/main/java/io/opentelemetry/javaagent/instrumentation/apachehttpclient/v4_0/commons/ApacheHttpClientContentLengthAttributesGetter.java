/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.commons;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.apachehttpclient.commons.BytesTransferMetrics;
import org.apache.http.HttpResponse;

public final class ApacheHttpClientContentLengthAttributesGetter
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
    BytesTransferMetrics.addAttributes(parentContext, attributes);
  }
}
