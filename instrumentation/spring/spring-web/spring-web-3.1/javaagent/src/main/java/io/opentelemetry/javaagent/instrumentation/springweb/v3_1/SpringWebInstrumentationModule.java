/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springweb.v3_1;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class SpringWebInstrumentationModule extends InstrumentationModule {
  public SpringWebInstrumentationModule() {
    super("spring-web", "spring-web-3.1");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // class added in 3.1
    return hasClassesNamed("org.springframework.web.method.HandlerMethod")
        // class added in 6.0
        .and(not(hasClassesNamed("org.springframework.web.ErrorResponse")));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new WebApplicationContextInstrumentation());
  }
}
