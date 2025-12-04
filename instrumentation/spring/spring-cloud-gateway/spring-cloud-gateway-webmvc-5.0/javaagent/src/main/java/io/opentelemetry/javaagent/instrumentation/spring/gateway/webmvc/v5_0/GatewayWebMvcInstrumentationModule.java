/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.gateway.webmvc.v5_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class GatewayWebMvcInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public GatewayWebMvcInstrumentationModule() {
    super("spring-cloud-gateway", "spring-cloud-gateway-webmvc-5.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // Spring Cloud Gateway Server WebMVC 5.0+
    return hasClassesNamed(
        "org.springframework.cloud.gateway.server.mvc.handler.GatewayDelegatingRouterFunction");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new GatewayDelegatingRouterFunctionInstrumentation());
  }

  @Override
  public String getModuleGroup() {
    // relies on servlet
    return "servlet";
  }

  @Override
  public int order() {
    // Later than Spring WebMVC
    return 1;
  }
}
