/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.activejhttp;

import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpServerInstrumenters;

/**
 * This class provides singleton instances and utilities for ActiveJ HTTP server instrumentation. It
 * is designed to centralize the creation and access of OpenTelemetry-related components, such as
 * the {@code Instrumenter} used for tracing HTTP requests and responses.
 *
 * @author Krishna Chaitanya Surapaneni
 */
public class ActiveJHttpServerConnectionSingletons {

  /**
   * The name of the instrumentation, used to identify this module in the OpenTelemetry framework.
   */
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.activej-http";

  private static final Instrumenter<HttpRequest, HttpResponse> INSTRUMENTER;

  static {
    INSTRUMENTER =
        JavaagentHttpServerInstrumenters.create(
            INSTRUMENTATION_NAME,
            new ActiveJHttpServerHttpAttributesGetter(),
            ActiveJHttpServerHeaders.INSTANCE);
  }

  public static Instrumenter<HttpRequest, HttpResponse> instrumenter() {
    return INSTRUMENTER;
  }

  private ActiveJHttpServerConnectionSingletons() {}
}
