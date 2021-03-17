/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import com.linecorp.armeria.common.logging.RequestLog;
import com.linecorp.armeria.server.ServiceRequestContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.ServerInstrumenter;
import java.util.Arrays;

final class ArmeriaServerInstrumenter
    extends ServerInstrumenter<ServiceRequestContext, RequestLog> {

  ArmeriaServerInstrumenter(OpenTelemetry openTelemetry) {
    super(
        openTelemetry,
        "io.opentelemetry.armeria-1.3",
        RequestContextGetter.INSTANCE,
        Arrays.asList(
            ArmeriaHttpAttributesExtractor.INSTANCE, ArmeriaNetAttributesExtractor.INSTANCE));
  }

  @Override
  protected String spanName(ServiceRequestContext request) {
    return request.config().route().patternString();
  }
}
