package io.opentelemetry.auto.instrumentation.couchbase.client;

import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.couchbase.client.java.CouchbaseCluster;
import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.CallDepthThreadLocalMap;
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
    super("couchbase");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface())
        .and(
            named("com.couchbase.client.java.bucket.DefaultAsyncBucketManager")
                .or(named("com.couchbase.client.java.CouchbaseAsyncBucket")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "rx.__OpenTelemetryTracingUtil",
      "io.opentelemetry.auto.decorator.BaseDecorator",
      "io.opentelemetry.auto.decorator.ClientDecorator",
      "io.opentelemetry.auto.decorator.DatabaseClientDecorator",
      "io.opentelemetry.auto.instrumentation.rxjava.SpanFinishingSubscription",
      "io.opentelemetry.auto.instrumentation.rxjava.TracedSubscriber",
      "io.opentelemetry.auto.instrumentation.rxjava.TracedOnSubscribe",
      packageName + ".CouchbaseClientDecorator",
      packageName + ".CouchbaseOnSubscribe",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
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

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void subscribeResult(
        @Advice.Enter final int callDepth,
        @Advice.Origin final Method method,
        @Advice.FieldValue("bucket") final String bucket,
        @Advice.Return(readOnly = false) Observable result) {
      if (callDepth > 0) {
        return;
      }
      CallDepthThreadLocalMap.reset(CouchbaseCluster.class);
      result = Observable.create(new CouchbaseOnSubscribe(result, method, bucket, null));
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

      // A query can be of many different types. We could track the creation of them and try to
      // rewind back to when they were created from a string, but for now we rely on toString()
      // returning something useful. That seems to be the case. If we're starting to see strange
      // query texts, this is the place to look!
      final String queryText = query != null ? query.toString() : null;
      result = Observable.create(new CouchbaseOnSubscribe(result, method, bucket, queryText));
    }
  }
}
