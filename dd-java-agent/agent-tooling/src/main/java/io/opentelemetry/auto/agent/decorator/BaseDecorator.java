package io.opentelemetry.auto.agent.decorator;

import io.opentelemetry.auto.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.AgentScope;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import io.opentelemetry.auto.instrumentation.api.Tags;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;

public abstract class BaseDecorator {

  protected BaseDecorator() {}

  protected abstract String[] instrumentationNames();

  protected abstract String spanType();

  protected abstract String component();

  public AgentSpan afterStart(final AgentSpan span) {
    assert span != null;
    if (spanType() != null) {
      span.setTag(MoreTags.SPAN_TYPE, spanType());
    }
    span.setTag(Tags.COMPONENT, component());
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

      span.setTag(Tags.PEER_HOSTNAME, remoteConnection.getHostName());
      span.setTag(Tags.PEER_PORT, remoteConnection.getPort());
    }
    return span;
  }

  public AgentSpan onPeerConnection(final AgentSpan span, final InetAddress remoteAddress) {
    assert span != null;
    if (remoteAddress != null) {
      span.setTag(Tags.PEER_HOSTNAME, remoteAddress.getHostName());
      if (remoteAddress instanceof Inet4Address) {
        span.setTag(Tags.PEER_HOST_IPV4, remoteAddress.getHostAddress());
      } else if (remoteAddress instanceof Inet6Address) {
        span.setTag(Tags.PEER_HOST_IPV6, remoteAddress.getHostAddress());
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
