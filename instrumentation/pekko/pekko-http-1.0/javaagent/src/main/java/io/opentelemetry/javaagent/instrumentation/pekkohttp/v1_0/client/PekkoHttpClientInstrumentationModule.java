/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.client;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class PekkoHttpClientInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public PekkoHttpClientInstrumentationModule() {
    super("pekko_http", "pekko_http_1.0", "pekko_http_client");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new HttpExtClientInstrumentation(), new PoolMasterActorInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
