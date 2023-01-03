/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.apachehttpclient.commons.BytesTransferMetrics;
import javax.annotation.Nonnull;
import org.apache.hc.core5.http.HttpResponse;

public final class ApacheHttpClientContentLengthAttributesGetter
    implements AttributesExtractor<ApacheHttpClientRequest, HttpResponse> {

  @Override
  public void onStart(
      @Nonnull AttributesBuilder attributes,
      @Nonnull Context parentContext,
      @Nonnull ApacheHttpClientRequest request) {}

  @Override
  public void onEnd(
      @Nonnull AttributesBuilder attributes,
      @Nonnull Context context,
      ApacheHttpClientRequest request,
      HttpResponse response,
      Throwable error) {
    Context parentContext = request.getParentContext();
    BytesTransferMetrics.addAttributes(parentContext, attributes);
  }
}
