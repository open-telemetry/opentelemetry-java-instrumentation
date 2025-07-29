/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.v2_3_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class XxlJobInstrumentationModule extends InstrumentationModule {

  public XxlJobInstrumentationModule() {
    super("xxl-job", "xxl-job-2.3.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed(
        "com.xxl.job.core.handler.impl.MethodJobHandler", "com.xxl.job.core.context.XxlJobHelper");
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
