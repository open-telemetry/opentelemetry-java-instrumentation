/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class SpymemcachedInstrumentationModule extends InstrumentationModule {

  public SpymemcachedInstrumentationModule() {
    super("spymemcached", "spymemcached-2.12");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new MemcachedClientInstrumentation());
  }
}
