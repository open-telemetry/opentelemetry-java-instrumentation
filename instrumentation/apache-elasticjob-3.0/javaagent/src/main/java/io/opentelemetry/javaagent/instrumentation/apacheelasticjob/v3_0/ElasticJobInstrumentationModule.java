/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class ElasticJobInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public ElasticJobInstrumentationModule() {
    super("apache-elasticjob", "apache-elasticjob-3.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed(
        "org.apache.shardingsphere.elasticjob.simple.executor.SimpleJobExecutor");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new SimpleJobExecutorInstrumentation(),
        new DataflowJobExecutorInstrumentation(),
        new ScriptJobExecutorInstrumentation(),
        new HttpJobExecutorInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
