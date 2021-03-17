/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.logging.RequestLog;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.ClientInstrumenter;
import java.util.Arrays;

final class ArmeriaClientInstrumenter
    extends ClientInstrumenter<ClientRequestContext, ClientRequestContext, RequestLog> {

  ArmeriaClientInstrumenter(OpenTelemetry openTelemetry) {
    super(
        openTelemetry,
        "io.opentelemetry.armeria-1.3",
        ClientRequestContextSetter.INSTANCE,
        Arrays.asList(
            ArmeriaHttpAttributesExtractor.INSTANCE, ArmeriaNetAttributesExtractor.INSTANCE));
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
