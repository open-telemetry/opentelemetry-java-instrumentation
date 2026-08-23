/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.grpc.v1_14;

import io.grpc.ServerInterceptor;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;

public final class ArmeriaGrpcSingletons {

  public static final ServerInterceptor SERVER_INTERCEPTOR =
      GrpcTelemetry.create(GlobalOpenTelemetry.get()).createServerInterceptor();

  private ArmeriaGrpcSingletons() {}
}
