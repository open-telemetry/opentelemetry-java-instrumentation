/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.clientv2.v0_8;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ClickHouseClientV2InstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public ClickHouseClientV2InstrumentationModule() {
    super("clickhouse_client_v2", "clickhouse_client_v2_0.8", "clickhouse", "clickhouse_client");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ClickHouseClientV2Instrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
