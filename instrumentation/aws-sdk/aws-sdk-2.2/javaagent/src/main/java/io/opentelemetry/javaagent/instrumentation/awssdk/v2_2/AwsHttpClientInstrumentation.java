/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v2_2;

import static io.opentelemetry.javaagent.instrumentation.awssdk.v2_2.TracingExecutionInterceptor.ScopeHolder.CURRENT;
import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.tooling.matcher.NameMatchers.namedOneOf;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import software.amazon.awssdk.core.internal.http.pipeline.stages.MakeAsyncHttpRequestStage;

/**
 * Separate instrumentation class to close aws request scope right after request has been submitted
 * for execution for Sync clients.
 */
final class AwsHttpClientInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed(
        "software.amazon.awssdk.core.internal.http.pipeline.stages.MakeHttpRequestStage");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return nameStartsWith("software.amazon.awssdk.")
        .and(
            extendsClass(
                namedOneOf(
                    "software.amazon.awssdk.core.internal.http.pipeline.stages.MakeHttpRequestStage",
                    "software.amazon.awssdk.core.internal.http.pipeline.stages.MakeAsyncHttpRequestStage")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod().and(isPublic()).and(named("execute")),
        AwsHttpClientInstrumentation.class.getName() + "$AwsHttpClientAdvice");
  }

  public static class AwsHttpClientAdvice {
    // scope.close here doesn't actually close the span.

    /**
     * FIXME: This is a hack to prevent netty instrumentation from messing things up.
     *
     * <p>Currently netty instrumentation cannot handle way AWS SDK makes http requests. If AWS SDK
     * make a netty call with active scope then continuation will be created that would never be
     * closed preventing whole trace from reporting. This happens because netty switches channels
     * between connection and request stages and netty instrumentation cannot find continuation
     * stored in channel attributes.
     */
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static boolean methodEnter(@Advice.This Object thiz) {
      if (thiz instanceof MakeAsyncHttpRequestStage) {
        Scope scope = CURRENT.get();
        if (scope != null) {
          CURRENT.set(null);
          scope.close();
          return true;
        }
      }
      return false;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(@Advice.Enter boolean scopeAlreadyClosed) {
      if (!scopeAlreadyClosed) {
        Scope scope = CURRENT.get();
        if (scope != null) {
          CURRENT.set(null);
          scope.close();
        }
      }
    }

    /**
     * This is to make muzzle think we need TracingExecutionInterceptor to make sure we do not apply
     * this instrumentation when TracingExecutionInterceptor would not work.
     */
    public static void muzzleCheck() {
      TracingExecutionInterceptor.muzzleCheck();
    }
  }
}
