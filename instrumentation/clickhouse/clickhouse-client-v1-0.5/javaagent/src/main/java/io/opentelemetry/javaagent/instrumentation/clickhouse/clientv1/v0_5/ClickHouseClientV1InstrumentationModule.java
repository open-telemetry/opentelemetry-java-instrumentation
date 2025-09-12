/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.clientv1.v0_5;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ClickHouseClientV1InstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public ClickHouseClientV1InstrumentationModule() {
    super("clickhouse-client-v1", "clickhouse-client-v1-0.5", "clickhouse", "clickhouse-client");
  }

  @Override
  public boolean isHelperClass(String className) {
    return "com.clickhouse.client.ClickHouseRequestAccess".equals(className);
  }

  @Override
  public List<String> injectedClassNames() {
    return singletonList("com.clickhouse.client.ClickHouseRequestAccess");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ClickHouseClientV1Instrumentation());
  }
}
