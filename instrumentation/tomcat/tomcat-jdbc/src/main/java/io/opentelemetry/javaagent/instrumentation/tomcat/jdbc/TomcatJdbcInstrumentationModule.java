/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.jdbc;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class TomcatJdbcInstrumentationModule extends InstrumentationModule {
  public TomcatJdbcInstrumentationModule() {
    super("tomcat-jdbc");
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("io.opentelemetry.javaagent.instrumentation");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new DataSourceProxyInstrumentation());
  }
}
