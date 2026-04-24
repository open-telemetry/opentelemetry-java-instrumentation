/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v3_4;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class CouchbaseInstrumentationModule extends InstrumentationModule {
  public CouchbaseInstrumentationModule() {
    super("couchbase", "couchbase-3.4");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed(
        // added in 3.4.0 (via com.couchbase.client:core-io 2.4.0)
        "com.couchbase.client.core.transaction.components.CoreTransactionRequest");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new CouchbaseEnvironmentInstrumentation());
  }
}
