/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v3_7;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class MongoClientInstrumentationModule extends InstrumentationModule {

  public MongoClientInstrumentationModule() {
    super("mongo", "mongo-3.7");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new MongoClientSettingsBuilderInstrumentation(),
        new InternalStreamConnectionInstrumentation(),
        new BaseClusterInstrumentation());
  }
}
