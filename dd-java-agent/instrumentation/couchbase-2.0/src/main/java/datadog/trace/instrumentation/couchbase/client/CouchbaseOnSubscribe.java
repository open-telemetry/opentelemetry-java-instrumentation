package datadog.trace.instrumentation.couchbase.client;

import static datadog.trace.instrumentation.couchbase.client.CouchbaseClientDecorator.DECORATE;

import datadog.trace.api.DDTags;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.instrumentation.rxjava.TracedOnSubscribe;
import java.lang.reflect.Method;
import rx.Observable;

public class CouchbaseOnSubscribe extends TracedOnSubscribe {
  private final String resourceName;
  private final String bucket;

  public CouchbaseOnSubscribe(
      final Observable originalObservable, final Method method, final String bucket) {
    super(originalObservable, "couchbase.call", DECORATE);

    final Class<?> declaringClass = method.getDeclaringClass();
    final String className =
        declaringClass.getSimpleName().replace("CouchbaseAsync", "").replace("DefaultAsync", "");
    resourceName = className + "." + method.getName();
    this.bucket = bucket;
  }

  @Override
  protected void afterStart(final AgentSpan span) {
    super.afterStart(span);

    span.setTag(DDTags.RESOURCE_NAME, resourceName);

    if (bucket != null) {
      span.setTag("bucket", bucket);
    }
  }
}
