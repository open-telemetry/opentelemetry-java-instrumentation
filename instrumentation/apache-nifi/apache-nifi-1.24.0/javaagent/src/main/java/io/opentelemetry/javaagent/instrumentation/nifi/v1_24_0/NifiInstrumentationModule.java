/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nifi.v1_24_0;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;

import java.util.List;

@SuppressWarnings("unused")
@AutoService(InstrumentationModule.class)
public class NifiInstrumentationModule extends InstrumentationModule {

  public NifiInstrumentationModule() {
    super("nifi", "apache-nifi", "nifi-1.24.0", "apache-nifi-1.24.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return java.util.Collections.singletonList(new CommitSession());
  }
}
