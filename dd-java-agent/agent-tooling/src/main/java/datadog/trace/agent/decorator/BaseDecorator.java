package datadog.trace.agent.decorator;

import static io.opentracing.log.Fields.ERROR_OBJECT;

import datadog.trace.api.Config;
import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.TreeSet;

public abstract class BaseDecorator {

  protected final boolean traceAnalyticsEnabled;
  protected final float traceAnalyticsSampleRate;

  protected BaseDecorator() {
    final String[] instrumentationNames = instrumentationNames();
    traceAnalyticsEnabled =
        instrumentationNames.length > 0
            && Config.traceAnalyticsIntegrationEnabled(
                new TreeSet<>(Arrays.asList(instrumentationNames)), traceAnalyticsDefault());
    float rate = 1.0f;
    for (final String name : instrumentationNames) {
      rate =
          Config.getFloatSettingFromEnvironment(
              "integration." + name + ".analytics.sample-rate", rate);
    }
    traceAnalyticsSampleRate = rate;
  }

  protected abstract String[] instrumentationNames();

  protected abstract String spanType();

  protected abstract String component();

  protected boolean traceAnalyticsDefault() {
    return false;
  }

  public Scope afterStart(final Scope scope) {
    assert scope != null;
    afterStart(scope.span());
    return scope;
  }

  public Span afterStart(final Span span) {
    assert span != null;
    if (spanType() != null) {
      span.setTag(DDTags.SPAN_TYPE, spanType());
    }
    Tags.COMPONENT.set(span, component());
    if (traceAnalyticsEnabled) {
      span.setTag(DDTags.ANALYTICS_SAMPLE_RATE, traceAnalyticsSampleRate);
    }
    return span;
  }

  public Scope beforeFinish(final Scope scope) {
    assert scope != null;
    beforeFinish(scope.span());
    return scope;
  }

  public Span beforeFinish(final Span span) {
    assert span != null;
    return span;
  }

  public Scope onError(final Scope scope, final Throwable throwable) {
    assert scope != null;
    onError(scope.span(), throwable);
    return scope;
  }

  public Span onError(final Span span, final Throwable throwable) {
    assert span != null;
    if (throwable != null) {
      Tags.ERROR.set(span, true);
      span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
    }
    return span;
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given method
   * reference. Anonymous classes are named based on their parent.
   *
   * @param method
   * @return
   */
  public String spanNameForMethod(final Method method) {
    final Class<?> declaringClass = method.getDeclaringClass();
    String className;
    if (declaringClass.isAnonymousClass()) {
      className = declaringClass.getName();
      if (declaringClass.getPackage() != null) {
        final String pkgName = declaringClass.getPackage().getName();
        if (!pkgName.isEmpty()) {
          className = declaringClass.getName().replace(pkgName, "").substring(1);
        }
      }
    } else {
      className = declaringClass.getSimpleName();
    }
    return className + "." + method.getName();
  }
}
