/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.couchbase.client.java.CouchbaseCluster;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import rx.Observable;

public class CouchbaseClusterInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "com.couchbase.client.java.cluster.DefaultAsyncClusterManager",
        "com.couchbase.client.java.CouchbaseAsyncCluster");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(isPublic()).and(returns(named("rx.Observable"))).and(not(named("core"))),
        CouchbaseClusterInstrumentation.class.getName() + "$CouchbaseClientAdvice");
  }

  @SuppressWarnings("unused")
  public static class CouchbaseClientAdvice {

    @Advice.OnMethodEnter
    public static int trackCallDepth() {
      return CallDepthThreadLocalMap.incrementCallDepth(CouchbaseCluster.class);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void subscribeResult(
        @Advice.Enter int callDepth,
        @Advice.Origin Method method,
        @Advice.Return(readOnly = false) Observable<?> result) {
      if (callDepth > 0) {
        return;
      }
      CallDepthThreadLocalMap.reset(CouchbaseCluster.class);

      result = Observable.create(CouchbaseOnSubscribe.create(result, null, method));
    }
  }
}
