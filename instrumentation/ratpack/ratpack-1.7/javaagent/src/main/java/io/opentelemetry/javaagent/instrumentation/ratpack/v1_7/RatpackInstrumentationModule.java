/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack.v1_7;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class RatpackInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public RatpackInstrumentationModule() {
    super("ratpack", "ratpack-1.7");
  }

  @Override
  public String getModuleGroup() {
    // relies on netty
    return "netty";
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // Only activate when running ratpack 1.7 or later
    return hasClassesNamed("ratpack.exec.util.retry.Delay");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new DefaultExecControllerInstrumentation(),
        new HttpClientInstrumentation(),
        new RequestActionSupportInstrumentation());
  }
}
