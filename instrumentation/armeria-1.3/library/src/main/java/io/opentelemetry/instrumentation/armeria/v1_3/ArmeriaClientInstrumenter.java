/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.logging.RequestLog;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.ClientInstrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.StatusExtractor;
import java.util.List;

final class ArmeriaClientInstrumenter
    extends ClientInstrumenter<ClientRequestContext, ClientRequestContext, RequestLog> {

  ArmeriaClientInstrumenter(
      OpenTelemetry openTelemetry,
      String instrumentationName,
      SpanNameExtractor<? super RequestContext> spanNameExtractor,
      StatusExtractor<? super RequestContext, ? super RequestLog> statusExtractor,
      List<? extends AttributesExtractor<? super ClientRequestContext, ? super RequestLog>>
          attributesExtractors) {
    super(
        openTelemetry,
        instrumentationName,
        spanNameExtractor,
        statusExtractor,
        ClientRequestContextSetter.INSTANCE,
        attributesExtractors);
  }

  @Override
  protected String spanName(ClientRequestContext clientRequestContext) {
    HttpRequest request = clientRequestContext.request();
    if (request != null) {
      return "HTTP " + request.method().name();
    }
    return "HTTP request";
  }
}
