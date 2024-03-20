/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.v1_9_2;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class XxlJobInstrumentationModule extends InstrumentationModule {

  public XxlJobInstrumentationModule() {
    super("xxl-job", "xxl-job-1.9.2");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // Class was added in 2.1.2
    return not(hasClassesNamed("com.xxl.job.core.handler.impl.MethodJobHandler"));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ScriptJobHandlerInstrumentation(),
        new SimpleJobHandlerInstrumentation(),
        new GlueJobHandlerInstrumentation());
  }
}
