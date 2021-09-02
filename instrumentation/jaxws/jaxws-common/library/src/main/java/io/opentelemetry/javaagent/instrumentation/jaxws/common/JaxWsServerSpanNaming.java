/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.common;

import io.opentelemetry.instrumentation.api.servlet.ServerSpanNameSupplier;

public class JaxWsServerSpanNaming {

  public static final ServerSpanNameSupplier<JaxWsRequest> SERVER_SPAN_NAME =
      (context, request) -> request.spanName();

  private JaxWsServerSpanNaming() {}
}
