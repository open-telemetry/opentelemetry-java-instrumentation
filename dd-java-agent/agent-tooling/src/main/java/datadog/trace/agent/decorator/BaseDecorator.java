package datadog.trace.agent.decorator;

import datadog.trace.api.Config;
import datadog.trace.api.DDTags;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
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
    final Config config = Config.get();
    final String[] instrumentationNames = instrumentationNames();
    traceAnalyticsEnabled =
        instrumentationNames.length > 0
            && config.isTraceAnalyticsIntegrationEnabled(
                new TreeSet<>(Arrays.asList(instrumentationNames)), traceAnalyticsDefault());
    traceAnalyticsSampleRate = config.getInstrumentationAnalyticsSampleRate(instrumentationNames);
  }

  protected abstract String[] instrumentationNames();

  protected abstract String spanType();

  protected abstract String component();

  protected boolean traceAnalyticsDefault() {
    return false;
  }

  public AgentSpan afterStart(final AgentSpan span) {
    assert span != null;
    if (spanType() != null) {
      span.setTag(DDTags.SPAN_TYPE, spanType());
    }
    span.setTag(Tags.COMPONENT.getKey(), component());
    if (traceAnalyticsEnabled) {
      span.setTag(DDTags.ANALYTICS_SAMPLE_RATE, traceAnalyticsSampleRate);
    }
    return span;
  }

  public AgentScope beforeFinish(final AgentScope scope) {
    assert scope != null;
    beforeFinish(scope.span());
    return scope;
  }

  public AgentSpan beforeFinish(final AgentSpan span) {
    assert span != null;
    return span;
  }

  public AgentScope onError(final AgentScope scope, final Throwable throwable) {
    assert scope != null;
    onError(scope.span(), throwable);
    return scope;
  }

  public AgentSpan onError(final AgentSpan span, final Throwable throwable) {
    assert span != null;
    if (throwable != null) {
      span.setError(true);
      span.addThrowable(throwable instanceof ExecutionException ? throwable.getCause() : throwable);
    }
    return span;
  }

  public AgentSpan onPeerConnection(
      final AgentSpan span, final InetSocketAddress remoteConnection) {
    assert span != null;
    if (remoteConnection != null) {
      onPeerConnection(span, remoteConnection.getAddress());

      span.setTag(Tags.PEER_HOSTNAME.getKey(), remoteConnection.getHostName());
      span.setTag(Tags.PEER_PORT.getKey(), remoteConnection.getPort());
    }
    return span;
  }

  public AgentSpan onPeerConnection(final AgentSpan span, final InetAddress remoteAddress) {
    assert span != null;
    if (remoteAddress != null) {
      span.setTag(Tags.PEER_HOSTNAME.getKey(), remoteAddress.getHostName());
      if (remoteAddress instanceof Inet4Address) {
        span.setTag(Tags.PEER_HOST_IPV4.getKey(), remoteAddress.getHostAddress());
      } else if (remoteAddress instanceof Inet6Address) {
        span.setTag(Tags.PEER_HOST_IPV6.getKey(), remoteAddress.getHostAddress());
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
