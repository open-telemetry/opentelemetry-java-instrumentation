/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc.datasource;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class DataSourceInstrumentationModule extends InstrumentationModule {
  public DataSourceInstrumentationModule() {
    // Data source instrumentation is default-off, so it must not share JDBC selectors.
    super("jdbc-datasource");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new DataSourceInstrumentation());
  }

  @Override
  public boolean defaultEnabled() {
    return false;
  }
}
