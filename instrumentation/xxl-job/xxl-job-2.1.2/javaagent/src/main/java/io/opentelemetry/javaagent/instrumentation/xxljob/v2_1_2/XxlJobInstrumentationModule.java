/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.v2_1_2;

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
    super("xxl-job", "xxl-job-2.1.2");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("com.xxl.job.core.handler.impl.MethodJobHandler")
        // Class was added in 2.3.0
        .and(not(hasClassesNamed("com.xxl.job.core.context.XxlJobHelper")));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new MethodJobHandlerInstrumentation(),
        new ScriptJobHandlerInstrumentation(),
        new SimpleJobHandlerInstrumentation(),
        new GlueJobHandlerInstrumentation());
  }
}
