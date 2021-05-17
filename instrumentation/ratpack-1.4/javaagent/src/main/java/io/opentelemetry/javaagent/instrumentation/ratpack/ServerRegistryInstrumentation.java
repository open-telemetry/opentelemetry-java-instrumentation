/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import ratpack.handling.HandlerDecorator;
import ratpack.registry.Registry;

public class ServerRegistryInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("ratpack.server.internal.ServerRegistry");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod().and(isStatic()).and(named("buildBaseRegistry")),
        ServerRegistryInstrumentation.class.getName() + "$BuildAdvice");
  }

  public static class BuildAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void injectTracing(@Advice.Return(readOnly = false) Registry registry) {
      registry =
          registry.join(
              Registry.builder().add(HandlerDecorator.prepend(TracingHandler.INSTANCE)).build());
    }
  }
}
