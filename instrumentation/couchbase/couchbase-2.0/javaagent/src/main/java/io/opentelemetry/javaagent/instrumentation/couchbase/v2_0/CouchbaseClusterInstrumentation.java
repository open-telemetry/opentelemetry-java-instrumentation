/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import static io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.CouchbaseSingletons.instrumenter;
import static io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.VirtualFieldHelper.COUCHBASE_SERVER_TARGET;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.couchbase.client.core.ClusterFacade;
import com.couchbase.client.java.CouchbaseCluster;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.rxjava.v1_0.TracedOnSubscribe;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v2_0.CouchbaseRequestInfo;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import rx.Observable;

class CouchbaseClusterInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "com.couchbase.client.java.cluster.DefaultAsyncClusterManager",
        "com.couchbase.client.java.CouchbaseAsyncCluster");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isPublic().and(returns(named("rx.Observable"))).and(not(named("core"))),
        getClass().getName() + "$CouchbaseClientAdvice");
  }

  @SuppressWarnings("unused")
  public static class CouchbaseClientAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope trackCallDepth(
        @Advice.Origin("#t") Class<?> declaringClass,
        @Advice.Origin("#m") String methodName,
        @Advice.FieldValue("core") ClusterFacade core) {
      CallDepth callDepth = CallDepth.forClass(CouchbaseCluster.class);
      if (callDepth.getAndIncrement() > 0) {
        return null;
      }
      CouchbaseRequestInfo request =
          CouchbaseRequestInfo.create(
              null, COUCHBASE_SERVER_TARGET.get(core), declaringClass, methodName);
      return CouchbaseRequestInfo.init(Context.current(), request).makeCurrent();
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    @SuppressWarnings("deprecation") // Observable.create is required for RxJava 1.0 compatibility
    public static Observable<?> subscribeResult(
        @Advice.Return Observable<?> result, @Advice.Enter Scope scope) {
      Context context = Context.current();
      if (scope != null) {
        scope.close();
      }
      CouchbaseRequestInfo request = CouchbaseRequestInfo.get(context);
      if (CallDepth.forClass(CouchbaseCluster.class).decrementAndGet() > 0
          || request == null
          || result == null) {
        return result;
      }
      return Observable.create(
          TracedOnSubscribe.perSubscription(result, instrumenter(), request.copySupplier()));
    }
  }
}
