/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

abstract class DelegatingInvocationHandler<T> implements InvocationHandler {

  private static final ClassLoader classLoader = DelegatingInvocationHandler.class.getClassLoader();

  protected final T delegate;

  DelegatingInvocationHandler(T delegate) {
    this.delegate = delegate;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    Object result;
    try {
      result = method.invoke(delegate, args);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
    // withOptions(...), added in SDK 2.x, returns a new view of the delegate that has to stay
    // instrumented
    Class<T> proxyType = getProxyType();
    if (method.getName().equals("withOptions") && method.getReturnType() == proxyType) {
      return wrap(proxyType.cast(result));
    }
    return result;
  }

  protected abstract Class<T> getProxyType();

  /** Returns a proxy that instruments {@code delegate} the same way this handler does. */
  protected abstract T wrap(T delegate);

  T createProxy() {
    Class<T> proxyType = getProxyType();
    Object proxy = Proxy.newProxyInstance(classLoader, new Class<?>[] {proxyType}, this);
    return proxyType.cast(proxy);
  }
}
