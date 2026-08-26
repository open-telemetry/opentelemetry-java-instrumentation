/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.network.v2_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class CouchbaseInstrumentationModule extends InstrumentationModule {

  public CouchbaseInstrumentationModule() {
    super("couchbase-network", "couchbase-network-2.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // NetworkResolution was added in core-io 1.6.0, which ships with Couchbase 2.6. Its absence
    // distinguishes the core-io 1.0-1.5 line handled by this module.
    return not(hasClassesNamed("com.couchbase.client.core.env.NetworkResolution"));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new CouchbaseCoreInstrumentation(), new CouchbaseNetworkInstrumentation());
  }
}
