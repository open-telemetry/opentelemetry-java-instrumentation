/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class CassandraClientInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public CassandraClientInstrumentationModule() {
    super("cassandra", "cassandra-4.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new SessionBuilderInstrumentation());
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed(
            // added in 4.0
            "com.datastax.oss.driver.api.core.CqlSession")
        .and(
            not(
                hasClassesNamed(
                    // added in 4.4
                    "com.datastax.dse.driver.api.core.cql.reactive.ReactiveSession")));
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
