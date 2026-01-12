/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import static io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.CouchbaseSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.couchbase.client.java.CouchbaseCluster;
import io.opentelemetry.instrumentation.rxjava.v1_0.TracedOnSubscribe;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
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
    public static CallDepth trackCallDepth() {
      CallDepth callDepth = CallDepth.forClass(CouchbaseCluster.class);
      callDepth.getAndIncrement();
      return callDepth;
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static Observable<?> subscribeResult(
        @Advice.Origin("#t") Class<?> declaringClass,
        @Advice.Origin("#m") String methodName,
        @Advice.Return Observable<?> result,
        @Advice.Enter CallDepth callDepth) {
      if (callDepth.decrementAndGet() > 0) {
        return result;
      }

      CouchbaseRequestInfo request = CouchbaseRequestInfo.create(null, declaringClass, methodName);
      return Observable.create(new TracedOnSubscribe<>(result, instrumenter(), request));
    }
  }
}
