/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.extension.matcher.ClassLoaderMatcher.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.ws.rs.client.Client;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

public class ClientBuilderInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("javax.ws.rs.client.ClientBuilder");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("javax.ws.rs.client.ClientBuilder"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("build").and(returns(implementsInterface(named("javax.ws.rs.client.Client")))),
        this.getClass().getName() + "$BuildAdvice");
  }

  public static class BuildAdvice {

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
