/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly.client;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.instrumentation.api.Pair;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import java.util.Map;

@AutoService(InstrumentationModule.class)
public class GrizzlyClientInstrumentationModule extends InstrumentationModule {
  public GrizzlyClientInstrumentationModule() {
    super("grizzly-client", "grizzly-client-1.9", "ning");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".GrizzlyClientTracer", packageName + ".GrizzlyInjectAdapter"
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new GrizzlyClientRequestInstrumentation(), new GrizzlyClientResponseInstrumentation());
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("com.ning.http.client.AsyncHandler", Pair.class.getName());
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }
}
