/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.geode.GeodeSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.geode.cache.Region;

public class GeodeRegionInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.geode.cache.Region");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.apache.geode.cache.Region"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(
                namedOneOf(
                        "clear",
                        "create",
                        "destroy",
                        "entrySet",
                        "get",
                        "getAll",
                        "invalidate",
                        "replace")
                    .or(nameStartsWith("contains"))
                    .or(nameStartsWith("keySet"))
                    .or(nameStartsWith("put"))
                    .or(nameStartsWith("remove"))),
        this.getClass().getName() + "$SimpleAdvice");
    transformer.applyAdviceToMethod(
        isMethod()
            .and(namedOneOf("existsValue", "query", "selectValue"))
            .and(takesArgument(0, String.class)),
        this.getClass().getName() + "$QueryAdvice");
  }

  @SuppressWarnings("unused")
  public static class SimpleAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This Region<?, ?> region,
        @Advice.Origin("#m") String methodName,
        @Advice.Local("otelRequest") GeodeRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      Context parentContext = currentContext();
      request = GeodeRequest.create(region, methodName, null);
      if (!instrumenter().shouldStart(parentContext, request)) {
        return;
      }

      context = instrumenter().start(parentContext, request);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelRequest") GeodeRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }

      scope.close();
      instrumenter().end(context, request, null, throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class QueryAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This Region<?, ?> region,
        @Advice.Origin("#m") String methodName,
        @Advice.Argument(0) String query,
        @Advice.Local("otelRequest") GeodeRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      Context parentContext = currentContext();
      request = GeodeRequest.create(region, methodName, query);
      if (!instrumenter().shouldStart(parentContext, request)) {
        return;
      }

      context = instrumenter().start(parentContext, request);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelRequest") GeodeRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }

      scope.close();
      instrumenter().end(context, request, null, throwable);
    }
  }
}
