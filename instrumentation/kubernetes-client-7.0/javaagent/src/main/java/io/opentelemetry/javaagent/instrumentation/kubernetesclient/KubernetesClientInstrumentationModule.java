/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class KubernetesClientInstrumentationModule extends InstrumentationModule {

  public KubernetesClientInstrumentationModule() {
    super("kubernetes-client", "kubernetes-client-7.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ApiClientInstrumentation());
  }
}
