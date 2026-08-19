/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachedbcp.v2_0;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ApacheDbcpInstrumentationModule extends InstrumentationModule {
  public ApacheDbcpInstrumentationModule() {
    super("apache-dbcp", "apache-dbcp-2.0");
  }

  @Override
  public boolean isHelperClass(String className) {
    return "org.apache.commons.dbcp2.OpenTelemetryBasicDataSourceUtil".equals(className);
  }

  @Override
  public List<String> injectedClassNames() {
    // must be injected into the application class loader so that it can access the package-private
    // BasicDataSource members it delegates to
    return singletonList("org.apache.commons.dbcp2.OpenTelemetryBasicDataSourceUtil");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new BasicDataSourceInstrumentation());
  }
}
