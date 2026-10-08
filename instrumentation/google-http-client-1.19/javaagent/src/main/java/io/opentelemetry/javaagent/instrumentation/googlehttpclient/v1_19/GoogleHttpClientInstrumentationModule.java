/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.googlehttpclient.v1_19;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class GoogleHttpClientInstrumentationModule extends InstrumentationModule {
  public GoogleHttpClientInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "google-http-client-1.19" : "google-http-client",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"google-http-client"}
            : new String[] {"google-http-client-1.19"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new GoogleHttpRequestInstrumentation());
  }
}
