/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.graphql.v20_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.internal.injection.ClassInjector;
import io.opentelemetry.javaagent.extension.instrumentation.internal.injection.InjectionMode;
import java.util.Collections;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@SuppressWarnings("unused")
@AutoService(InstrumentationModule.class)
public class GraphqlInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public GraphqlInstrumentationModule() {
    super("graphql-java", "graphql-java-20.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 20.0
    return hasClassesNamed("graphql.execution.instrumentation.SimplePerformantInstrumentation");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Collections.singletonList(new GraphqlInstrumentation());
  }

  @Override
  public void injectClasses(ClassInjector injector) {
    // we do not use ByteBuddy Advice dispatching in this instrumentation
    // Instead, we manually call GraphqlSingletons via ASM
    // Easiest solution to work with indy is to inject an indy-proxy to be invoked
    injector
        .proxyBuilder("io.opentelemetry.javaagent.instrumentation.graphql.v20_0.GraphqlSingletons")
        .inject(InjectionMode.CLASS_ONLY);
  }
}
