/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongoasync.v3_3;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class MongoAsyncClientInstrumentationModule extends InstrumentationModule {

  public MongoAsyncClientInstrumentationModule() {
    super("mongo-async", "mongo-async-3.3", "mongo");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new MongoClientSettingsBuildersInstrumentation(),
        new InternalStreamConnectionInstrumentation(),
        new BaseClusterInstrumentation());
  }
}
