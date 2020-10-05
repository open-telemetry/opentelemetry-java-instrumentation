/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.grpc.client;

import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Map;

abstract class AbstractGrpcClientInstrumentation extends Instrumenter.Default {

  public AbstractGrpcClientInstrumentation() {
    super("grpc", "grpc-client");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".GrpcClientTracer",
      packageName + ".GrpcInjectAdapter",
      packageName + ".TracingClientInterceptor",
      packageName + ".TracingClientInterceptor$TracingClientCall",
      packageName + ".TracingClientInterceptor$TracingClientCallListener",
      "io.opentelemetry.instrumentation.auto.grpc.common.GrpcHelper",
    };
  }

  @Override
  public Map<String, String> contextStore() {
    return Collections.singletonMap(
        "io.grpc.ManagedChannelBuilder", InetSocketAddress.class.getName());
  }
}
