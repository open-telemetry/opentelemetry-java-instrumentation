/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.v0_6;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.Collections;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ClickHouseClientV2InstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public ClickHouseClientV2InstrumentationModule() {
    super("clickhouse-client", "clickhouse-client-0.6", "clickhouse");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Collections.singletonList(new ClickHouseClientV2Instrumentation());
  }
}
