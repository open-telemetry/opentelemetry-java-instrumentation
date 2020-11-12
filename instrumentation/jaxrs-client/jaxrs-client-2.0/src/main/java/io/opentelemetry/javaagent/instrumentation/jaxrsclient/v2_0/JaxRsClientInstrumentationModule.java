/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.hasInterface;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import java.util.Map;
import javax.ws.rs.client.Client;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public final class JaxRsClientInstrumentationModule extends InstrumentationModule {

  public JaxRsClientInstrumentationModule() {
    super("jax-rs", "jaxrs", "jax-rs-client");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".JaxRsClientTracer",
      packageName + ".ClientTracingFeature",
      packageName + ".ClientTracingFilter",
      packageName + ".InjectAdapter",
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ClientBuilderInstrumentation());
  }

  private static final class ClientBuilderInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<ClassLoader> classLoaderMatcher() {
      // Optimization for expensive typeMatcher.
      return hasClassesNamed("javax.ws.rs.client.ClientBuilder");
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return extendsClass(named("javax.ws.rs.client.ClientBuilder"));
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          named("build").and(returns(hasInterface(named("javax.ws.rs.client.Client")))),
          JaxRsClientInstrumentationModule.class.getName() + "$ClientBuilderAdvice");
    }
  }

  public static class ClientBuilderAdvice {

    @Advice.OnMethodExit
    public static void registerFeature(
        @Advice.Return(typing = Assigner.Typing.DYNAMIC) Client client) {
      // Register on the generated client instead of the builder
      // The build() can be called multiple times and is not thread safe
      // A client is only created once
      client.register(ClientTracingFeature.class);
    }
  }
}
