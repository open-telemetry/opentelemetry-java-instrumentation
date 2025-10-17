/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
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
import javax.annotation.Nullable;
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

  public static class AdviceScope {
    private final GeodeRequest request;
    private final Context context;
    private final Scope scope;

    public AdviceScope(GeodeRequest request, Context context, Scope scope) {
      this.request = request;
      this.context = context;
      this.scope = scope;
    }

    @Nullable
    public static AdviceScope start(
        Region<?, ?> region, String methodName, @Nullable String query) {
      Context parentContext = currentContext();
      GeodeRequest request = GeodeRequest.create(region, methodName, query);
      if (!instrumenter().shouldStart(parentContext, request)) {
        return null;
      }

      Context context = instrumenter().start(parentContext, request);
      return new AdviceScope(request, context, context.makeCurrent());
    }

    public void end(@Nullable Throwable throwable) {
      if (scope != null) {
        scope.close();
      }
      instrumenter().end(context, request, null, throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class SimpleAdvice {

    public static class AdviceLocals {
      public GeodeRequest request;
      public Context context;
      public Scope scope;
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(
        @Advice.This Region<?, ?> region, @Advice.Origin("#m") String methodName) {
      return AdviceScope.start(region, methodName, null);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class QueryAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(
        @Advice.This Region<?, ?> region,
        @Advice.Origin("#m") String methodName,
        @Advice.Argument(0) String query) {
      return AdviceScope.start(region, methodName, query);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }
}
