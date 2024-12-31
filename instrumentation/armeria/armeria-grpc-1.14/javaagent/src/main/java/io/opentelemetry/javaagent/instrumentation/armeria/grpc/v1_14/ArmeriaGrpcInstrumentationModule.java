/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.grpc.v1_14;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ArmeriaGrpcInstrumentationModule extends InstrumentationModule {
  public ArmeriaGrpcInstrumentationModule() {
    super("armeria-grpc", "armeria-grpc-1.14", "armeria", "armeria-1.14");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ArmeriaGrpcClientBuilderInstrumentation(),
        new ArmeriaGrpcServiceBuilderInstrumentation());
  }
}
