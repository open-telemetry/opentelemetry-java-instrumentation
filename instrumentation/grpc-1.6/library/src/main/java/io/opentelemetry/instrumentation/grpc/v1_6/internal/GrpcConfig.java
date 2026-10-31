/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6.internal;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import java.util.List;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class GrpcConfig {

  private final List<String> clientRequestCapturedHeaders;
  private final List<String> serverRequestCapturedHeaders;

  public static GrpcConfig create(OpenTelemetry openTelemetry) {
    return new GrpcConfig(DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "grpc"));
  }

  GrpcConfig(DeclarativeConfigProperties config) {
    clientRequestCapturedHeaders =
        config.get("client").getScalarList("request_captured_headers", String.class, emptyList());
    serverRequestCapturedHeaders =
        config.get("server").getScalarList("request_captured_headers", String.class, emptyList());
  }

  public List<String> getClientRequestCapturedHeaders() {
    return clientRequestCapturedHeaders;
  }

  public List<String> getServerRequestCapturedHeaders() {
    return serverRequestCapturedHeaders;
  }
}
