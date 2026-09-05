/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v3_1;

import static io.opentelemetry.javaagent.extension.instrumentation.internal.DeprecatedInstrumentationNames.expandDeprecatedNames;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class CouchbaseInstrumentationModule extends InstrumentationModule {
  public CouchbaseInstrumentationModule() {
    super(
        "couchbase",
        expandDeprecatedNames("couchbase-3.1|deprecated:couchbase-3.1.6", "couchbase"));
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 3.1.0 (via com.couchbase.client:core-io 2.1.0)
    return hasClassesNamed("com.couchbase.client.core.cnc.TracingIdentifiers")
        // added in 3.2.0 (via com.couchbase.client:core-io 2.2.0)
        .and(not(hasClassesNamed("com.couchbase.client.core.cnc.RequestSpan$StatusCode")));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new CouchbaseEnvironmentInstrumentation());
  }
}
