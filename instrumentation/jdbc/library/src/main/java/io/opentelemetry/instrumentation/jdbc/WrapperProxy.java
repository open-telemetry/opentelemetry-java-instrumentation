/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Copyright 2017-2021 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.opentelemetry.instrumentation.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** Utility class for the creation of wrapper proxies. */
public final class WrapperProxy {

  private interface WrapperInvocationHandler<T> extends InvocationHandler {

    T getObject();

    T getWrapper();
  }

  /**
   * Returns a {@linkplain Proxy proxy} with type {@code <T>} of the specified {@code obj} wrapping
   * the provided {@code wrapper} instance. Method invocations on the proxy instance will be handled
   * as such:
   *
   * <ol>
   *   <li>The {@code wrapper} instance will be invoked for methods that <i>exist</i> in the {@code
   *       wrapper} instance.
   *   <li>The {@code obj} instance will be invoked for methods that <i>do not exist</i> in the
   *       {@code wrapper} instance.
   * </ol>
   *
   * The type of the returned {@linkplain Proxy proxy} will be the composition of all
   * super-interfaces of the runtime type of the specified {@code obj} instance.
   *
   * <p><b>Note:</b> The runtime type of the returned instance will <i>not be</i> of a concrete
   * class, but rather a composition of all super-interfaces of the concrete class {@code <T>}. It
   * is therefore not possible to cast the instance returned by this method to a concrete class, but
   * rather any super-interface of the concrete class.
   *
   * @param <T> The type parameter of the specified arguments.
   * @param obj The target object instance to wrap.
   * @param wrapper The wrapping object.
   * @return a {@linkplain Proxy proxy} with type {@code <T>} of the specified {@code obj} wrapping
   *     wrapping the provided {@code wrapper} instance, or, {@code wrapper} if {@code obj ==
   *     wrapper}, or if {@code target} or {@code wrapper} is null.
   */
  @SuppressWarnings("unchecked")
  public static <T> T wrap(final T obj, final T wrapper) {
    if (obj == null || wrapper == null || obj == wrapper) {
      return wrapper;
    }

    final Class<?> objClass = obj.getClass();
    final Class<?> wrapperClass = wrapper.getClass();
    return (T)
        Proxy.newProxyInstance(
            objClass.getClassLoader(),
            Classes.getAllInterfaces(objClass),
            new WrapperInvocationHandler<T>() {
              @Override
              public Object invoke(final Object proxy, final Method method, final Object[] args)
                  throws Throwable {
                try {
                  try {
                    if (method.getDeclaringClass().isAssignableFrom(wrapperClass)) {
                      return method.invoke(wrapper, args);
                    }

                    final Method specific =
                        Classes.getDeclaredMethodDeep(
                            wrapperClass, method.getName(), method.getParameterTypes());
                    if (specific != null) {
                      return specific.invoke(wrapper, args);
                    }
                  } catch (final IllegalArgumentException ignored) {
                    // nothing to do, expected exception
                  }

                  return method.invoke(obj, args);
                } catch (final InvocationTargetException e) {
                  throw e.getCause();
                } catch (final IllegalAccessException e) {
                  final IllegalAccessError error = new IllegalAccessError(e.getMessage());
                  error.setStackTrace(e.getStackTrace());
                  throw error;
                }
              }

              @Override
              public T getObject() {
                return obj;
              }

              @Override
              public T getWrapper() {
                return wrapper;
              }
            });
  }

  /**
   * Tests whether the specified object is a proxy of a wrapped object.
   *
   * @param obj The object to test.
   * @return Whether the specified object is a proxy of a wrapped object.
   * @throws NullPointerException If {@code obj} is null.
   */
  public static boolean isWrapper(final Object obj) {
    return Proxy.isProxyClass(obj.getClass())
        && Proxy.getInvocationHandler(obj) instanceof WrapperInvocationHandler;
  }

  /**
   * Tests whether the specified object is a proxy of a wrapped instance type matching the provided
   * {@code wrappedClass}.
   *
   * @param <T> The common type of the proxy and wrapped instance.
   * @param obj The object to test.
   * @param wrappedClass The type of the instance wrapped by the specified {@code obj}.
   * @return Whether the specified object is a proxy of a wrapped instance type matching the
   *     provided {@code wrappedClass}.
   * @throws NullPointerException If {@code obj} or {@code wrappedClass} is null.
   */
  public static <T> boolean isWrapper(final T obj, final Class<? extends T> wrappedClass) {
    if (!Proxy.isProxyClass(obj.getClass())) {
      return false;
    }

    final InvocationHandler handler = Proxy.getInvocationHandler(obj);
    if (!(handler instanceof WrapperInvocationHandler)) {
      return false;
    }

    return wrappedClass.isAssignableFrom(
        ((WrapperInvocationHandler<?>) handler).getWrapper().getClass());
  }

  private WrapperProxy() {}
}
