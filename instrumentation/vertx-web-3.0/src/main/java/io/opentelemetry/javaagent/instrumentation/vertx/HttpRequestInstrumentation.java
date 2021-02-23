/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx;

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPrivate;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import io.vertx.core.http.HttpClientRequest;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * This hooks into two points in Vertx HttpClientRequest lifecycle.
 *
 * <p>First, when request is finished by the client, meaning that it is ready to be sent out, then
 * {@link AttachContextAdvice} attaches current context to that request.
 *
 * <p>Second, when HttpClientRequest calls any method that actually performs write on the underlying
 * Netty channel {@link MountContextAdvice} scopes that method call into the context captured on the
 * first step.
 *
 * <p>This ensures proper context transfer between the client who actually initiated the http call
 * and the Netty Channel that will perform that operation.
 */
public class HttpRequestInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("io.vertx.core.http.HttpClientRequest");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("io.vertx.core.http.HttpClientRequest"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();

    transformers.put(
        isMethod().and(nameStartsWith("end")),
        HttpRequestInstrumentation.class.getName() + "$AttachContextAdvice");

    transformers.put(
        isMethod().and(isPrivate()).and(nameStartsWith("write").or(nameStartsWith("connected"))),
        HttpRequestInstrumentation.class.getName() + "$MountContextAdvice");
    return transformers;
  }

  public static class AttachContextAdvice {
    @Advice.OnMethodEnter
    public static void attachContext(@Advice.This HttpClientRequest request) {
      InstrumentationContext.get(HttpClientRequest.class, Context.class)
          .put(request, Java8BytecodeBridge.currentContext());
    }
  }

  public static class MountContextAdvice {
    @Advice.OnMethodEnter
    public static Scope mountContext(@Advice.This HttpClientRequest request) {
      Context context =
          InstrumentationContext.get(HttpClientRequest.class, Context.class).get(request);
      return context == null ? null : context.makeCurrent();
    }

    @Advice.OnMethodExit
    public static void unmountContext(@Advice.Enter Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
