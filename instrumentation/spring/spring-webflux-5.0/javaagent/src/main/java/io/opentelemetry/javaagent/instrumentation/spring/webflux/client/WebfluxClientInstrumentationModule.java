/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.client;

import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class WebfluxClientInstrumentationModule extends InstrumentationModule {

  public WebfluxClientInstrumentationModule() {
    super("spring-webflux", "spring-webflux-5.0", "spring-webflux-client");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.instrumentation.spring.webflux.client.SpringWebfluxHttpClientTracer",
      "io.opentelemetry.instrumentation.spring.webflux.client.HttpHeadersInjectAdapter",
      "io.opentelemetry.instrumentation.spring.webflux.client.WebClientTracingFilter",
      "io.opentelemetry.instrumentation.spring.webflux.client.WebClientTracingFilter$MonoWebClientTrace",
      "io.opentelemetry.instrumentation.spring.webflux.client.TraceWebClientSubscriber"
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new WebClientBuilderInstrumentation());
  }

  private static final class WebClientBuilderInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<ClassLoader> classLoaderMatcher() {
      // Optimization for expensive typeMatcher.
      return hasClassesNamed("org.springframework.web.reactive.function.client.WebClient");
    }

    @Override
    public ElementMatcher<? super TypeDescription> typeMatcher() {
      return implementsInterface(
          named("org.springframework.web.reactive.function.client.WebClient$Builder"));
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          isMethod().and(isPublic()).and(named("build")), WebClientFilterAdvice.class.getName());
    }
  }
}
