/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.couchbase.v2_0;

import static io.opentelemetry.auto.tooling.matcher.NameMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.couchbase.client.java.CouchbaseCluster;
import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.CallDepthThreadLocalMap;
import io.opentelemetry.auto.bootstrap.instrumentation.jdbc.DbSystem;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import rx.Observable;

@AutoService(Instrumenter.class)
public class CouchbaseBucketInstrumentation extends Instrumenter.Default {

  public CouchbaseBucketInstrumentation() {
    super(DbSystem.COUCHBASE);
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "com.couchbase.client.java.bucket.DefaultAsyncBucketManager",
        "com.couchbase.client.java.CouchbaseAsyncBucket");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "rx.__OpenTelemetryTracingUtil",
      "io.opentelemetry.auto.instrumentation.rxjava.SpanFinishingSubscription",
      "io.opentelemetry.auto.instrumentation.rxjava.TracedSubscriber",
      "io.opentelemetry.auto.instrumentation.rxjava.TracedOnSubscribe",
      packageName + ".CouchbaseClientDecorator",
      packageName + ".CouchbaseOnSubscribe",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod().and(isPublic()).and(returns(named("rx.Observable"))).and(not(named("query"))),
        CouchbaseBucketInstrumentation.class.getName() + "$CouchbaseClientAdvice");
    transformers.put(
        isMethod().and(isPublic()).and(returns(named("rx.Observable"))).and(named("query")),
        CouchbaseBucketInstrumentation.class.getName() + "$CouchbaseClientQueryAdvice");
    return transformers;
  }

  public static class CouchbaseClientAdvice {

    @Advice.OnMethodEnter
    public static int trackCallDepth() {
      return CallDepthThreadLocalMap.incrementCallDepth(CouchbaseCluster.class);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void subscribeResult(
        @Advice.Enter final int callDepth,
        @Advice.Origin final Method method,
        @Advice.FieldValue("bucket") final String bucket,
        @Advice.Return(readOnly = false) Observable result) {
      if (callDepth > 0) {
        return;
      }
      CallDepthThreadLocalMap.reset(CouchbaseCluster.class);
      result = Observable.create(CouchbaseOnSubscribe.create(result, bucket, method));
    }
  }

  public static class CouchbaseClientQueryAdvice {

    @Advice.OnMethodEnter
    public static int trackCallDepth() {
      return CallDepthThreadLocalMap.incrementCallDepth(CouchbaseCluster.class);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void subscribeResult(
        @Advice.Enter final int callDepth,
        @Advice.Origin final Method method,
        @Advice.FieldValue("bucket") final String bucket,
        @Advice.Argument(value = 0, optional = true) final Object query,
        @Advice.Return(readOnly = false) Observable result) {
      if (callDepth > 0) {
        return;
      }
      CallDepthThreadLocalMap.reset(CouchbaseCluster.class);

      if (query != null) {
        // A query can be of many different types. We could track the creation of them and try to
        // rewind back to when they were created from a string, but for now we rely on toString()
        // returning something useful. That seems to be the case. If we're starting to see strange
        // query texts, this is the place to look!
        result = Observable.create(CouchbaseOnSubscribe.create(result, bucket, query.toString()));
      } else {
        result = Observable.create(CouchbaseOnSubscribe.create(result, bucket, method));
      }
    }
  }
}
