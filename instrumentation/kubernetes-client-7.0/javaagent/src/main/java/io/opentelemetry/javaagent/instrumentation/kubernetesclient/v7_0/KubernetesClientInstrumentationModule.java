/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient.v7_0;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class KubernetesClientInstrumentationModule extends InstrumentationModule {

  public KubernetesClientInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "kubernetes-client-7.0" : "kubernetes-client",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"kubernetes-client"}
            : new String[] {"kubernetes-client-7.0"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ApiClientInstrumentation());
  }
}
