/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.openfeign;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class OpenFeignInstrumentationModule extends InstrumentationModule {

  public OpenFeignInstrumentationModule() {
    super("openfeign", "openfeign-9.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("feign.SynchronousMethodHandler");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new FeignSynchronousMethodHandlerInstrumentation(), new FeignClientInstrumentation());
  }
}
