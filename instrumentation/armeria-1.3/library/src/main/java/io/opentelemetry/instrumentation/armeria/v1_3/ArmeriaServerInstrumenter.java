/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.logging.RequestLog;
import com.linecorp.armeria.server.ServiceRequestContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.ServerInstrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.StatusExtractor;
import java.util.List;

final class ArmeriaServerInstrumenter
    extends ServerInstrumenter<ServiceRequestContext, RequestLog> {

  ArmeriaServerInstrumenter(
      OpenTelemetry openTelemetry,
      String instrumentationName,
      SpanNameExtractor<? super RequestContext> spanNameExtractor,
      StatusExtractor<? super RequestContext, ? super RequestLog> statusExtractor,
      List<? extends AttributesExtractor<? super ServiceRequestContext, ? super RequestLog>>
          attributesExtractors) {
    super(
        openTelemetry,
        instrumentationName,
        spanNameExtractor,
        statusExtractor,
        RequestContextGetter.INSTANCE,
        attributesExtractors);
  }

  @Override
  protected String spanName(ServiceRequestContext request) {
    return request.config().route().patternString();
  }
}
