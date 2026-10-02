/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.web.v6_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class SpringWebInstrumentationModule extends InstrumentationModule {

  public SpringWebInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "spring-web-6.0" : "spring-web",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"spring-web"}
            : new String[] {"spring-web-6.0"});
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 6.0
    return hasClassesNamed("org.springframework.web.ErrorResponse");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new RestTemplateInstrumentation());
  }
}
