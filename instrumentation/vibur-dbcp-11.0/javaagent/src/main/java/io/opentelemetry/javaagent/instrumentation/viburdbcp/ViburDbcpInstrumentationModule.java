/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.viburdbcp;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class ViburDbcpInstrumentationModule extends InstrumentationModule {
  public ViburDbcpInstrumentationModule() {
    super("vibur-dbcp", "vibur-dbcp-11.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // ViburDBCPConfig was renamed in 10.0; this matcher excludes all versions < 10.0
    // in 11.0, the ViburDBCPDataSource#getPool() method signature was changed - this is detected by
    // muzzle
    return not(hasClassesNamed("org.vibur.dbcp.ViburDBCPConfig"));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ViburDbcpDataSourceInstrumentation());
  }
}
