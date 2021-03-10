/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import com.linecorp.armeria.common.logging.RequestLog;
import com.linecorp.armeria.server.ServiceRequestContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.Arrays;

final class ArmeriaServerInstrumenter extends Instrumenter<ServiceRequestContext, RequestLog> {

  ArmeriaServerInstrumenter(OpenTelemetry openTelemetry) {
    super(
        openTelemetry.getTracer("io.opentelemetry.armeria-1.3"),
        Arrays.asList(ArmeriaHttpExtractor.INSTANCE, ArmeriaNetExtractor.INSTANCE));
  }

  @Override
  protected String spanName(ServiceRequestContext request) {
    return request.config().route().patternString();
  }

  @Override
  protected SpanKind spanKind(ServiceRequestContext serviceRequestContext) {
    return SpanKind.SERVER;
  }
}
