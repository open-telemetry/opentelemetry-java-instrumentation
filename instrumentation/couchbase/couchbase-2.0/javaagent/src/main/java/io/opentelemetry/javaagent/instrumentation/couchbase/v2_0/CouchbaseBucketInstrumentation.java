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

public class CouchbaseBucketInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "com.couchbase.client.java.bucket.DefaultAsyncBucketManager",
        "com.couchbase.client.java.CouchbaseAsyncBucket");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(isPublic()).and(returns(named("rx.Observable"))).and(not(named("query"))),
        CouchbaseBucketInstrumentation.class.getName() + "$CouchbaseClientAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(isPublic()).and(returns(named("rx.Observable"))).and(named("query")),
        CouchbaseBucketInstrumentation.class.getName() + "$CouchbaseClientQueryAdvice");
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
        @Advice.FieldValue("bucket") String bucket,
        @Advice.Return(readOnly = false) Observable<?> result) {
      if (callDepth > 0) {
        return;
      }
      CallDepthThreadLocalMap.reset(CouchbaseCluster.class);
      result = Observable.create(CouchbaseOnSubscribe.create(result, bucket, method));
    }
  }

  @SuppressWarnings("unused")
  public static class CouchbaseClientQueryAdvice {

    @Advice.OnMethodEnter
    public static int trackCallDepth() {
      return CallDepthThreadLocalMap.incrementCallDepth(CouchbaseCluster.class);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void subscribeResult(
        @Advice.Enter int callDepth,
        @Advice.Origin Method method,
        @Advice.FieldValue("bucket") String bucket,
        @Advice.Argument(value = 0, optional = true) Object query,
        @Advice.Return(readOnly = false) Observable<?> result) {
      if (callDepth > 0) {
        return;
      }
      CallDepthThreadLocalMap.reset(CouchbaseCluster.class);

      if (query != null) {
        // A query can be of many different types. We could track the creation of them and try to
        // rewind back to when they were created from a string, but for now we rely on toString()
        // returning something useful. That seems to be the case. If we're starting to see strange
        // query texts, this is the place to look!
        result = Observable.create(CouchbaseOnSubscribe.create(result, bucket, query));
      } else {
        result = Observable.create(CouchbaseOnSubscribe.create(result, bucket, method));
      }
    }
  }
}
