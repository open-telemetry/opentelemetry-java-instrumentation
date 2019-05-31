package datadog.trace.agent.decorator;

import static io.opentracing.log.Fields.ERROR_OBJECT;
import static java.util.Collections.singletonMap;

import datadog.trace.api.Config;
import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;

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
      rate = Config.getFloatSettingFromEnvironment(name + ".analytics.sample-rate", rate);
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
      span.log(
          singletonMap(
              ERROR_OBJECT,
              throwable instanceof ExecutionException ? throwable.getCause() : throwable));
    }
    return span;
  }

  public Span onPeerConnection(final Span span, final InetSocketAddress remoteConnection) {
    assert span != null;
    if (remoteConnection != null) {
      onPeerConnection(span, remoteConnection.getAddress());

      span.setTag(Tags.PEER_HOSTNAME.getKey(), remoteConnection.getHostName());
      span.setTag(Tags.PEER_PORT.getKey(), remoteConnection.getPort());
    }
    return span;
  }

  public Span onPeerConnection(final Span span, final InetAddress remoteAddress) {
    assert span != null;
    if (remoteAddress != null) {
      span.setTag(Tags.PEER_HOSTNAME.getKey(), remoteAddress.getHostName());
      if (remoteAddress instanceof Inet4Address) {
        Tags.PEER_HOST_IPV4.set(span, remoteAddress.getHostAddress());
      } else if (remoteAddress instanceof Inet6Address) {
        Tags.PEER_HOST_IPV6.set(span, remoteAddress.getHostAddress());
      }
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
    return spanNameForClass(method.getDeclaringClass()) + "." + method.getName();
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given class
   * reference. Anonymous classes are named based on their parent.
   *
   * @param clazz
   * @return
   */
  public String spanNameForClass(final Class clazz) {
    if (!clazz.isAnonymousClass()) {
      return clazz.getSimpleName();
    }
    String className = clazz.getName();
    if (clazz.getPackage() != null) {
      final String pkgName = clazz.getPackage().getName();
      if (!pkgName.isEmpty()) {
        className = clazz.getName().replace(pkgName, "").substring(1);
      }
    }
    return className;
  }
}
