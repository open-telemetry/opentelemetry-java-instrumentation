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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;

public final class Classes {

  /**
   * Adds all interfaces extended by the specified {@code iface} interface {@link Class}.
   *
   * @param iface The interface {@link Class}.
   * @param set   The set into which all extended interfaces are to be added.
   * @throws NullPointerException If {@code iface} or {@code set} is null.
   */
  private static void recurse(final Class<?> iface, final HashSet<Class<?>> set) {
    if (set.contains(iface)) {
      return;
    }

    set.add(iface);
    for (final Class<?> extended : iface.getInterfaces()) {
      recurse(extended, set);
    }
  }

  /**
   * Returns all interfaces implemented by the class or interface represented by the specified
   * class. This method differentiates itself from {@link Class#getInterfaces()} by returning
   * <i>all</i> interfaces (full depth and breadth) instead of just the interfaces <i>directly</i>
   * implemented by the class.
   *
   * @param cls The class.
   * @return All interfaces implemented by the class or interface represented by the specified
   * class.
   * @throws NullPointerException If {@code cls} is null.
   */
  public static Class<?>[] getAllInterfaces(final Class<?> cls) {
    Class<?> parent = cls;
    Class<?>[] ifaces = null;
    HashSet<Class<?>> set = null;
    do {
      ifaces = parent.getInterfaces();
      if (ifaces.length == 0) {
        continue;
      }

      if (set == null) {
        set = new HashSet<>(4);
      }

      for (final Class<?> iface : ifaces) {
        recurse(iface, set);
      }
    }
    while ((parent = parent.getSuperclass()) != null);
    return set == null ? ifaces : set.toArray(new Class[set.size()]);
  }

  /**
   * Returns a Method object that reflects the specified declared method of the class or interface
   * represented by {@code cls} (excluding inherited methods), or {@code null} if the method is not
   * found.
   * <p>
   * Declared methods include public, protected, default (package) access, and private visibility.
   * <p>
   * The {@code name} parameter is a String that specifies the simple name of the desired method,
   * and the {@code parameterTypes} parameter is an array of Class objects that identify the
   * method's formal parameter types, in declared order. If more than one method with the same
   * parameter types is declared in a class, and one of these methods has a return type that is more
   * specific than any of the others, that method is returned; otherwise one of the methods is
   * chosen arbitrarily. If the name is {@code "<init>"} or {@code "<clinit>"} this method returns
   * {@code null}. If this Class object represents an array type, then this method does not find the
   * clone() method.
   * <p>
   * This method differentiates itself from {@link Class#getDeclaredMethod(String, Class...)} by
   * returning {@code null} when a method is not found, instead of throwing {@link
   * NoSuchMethodException}.
   *
   * @param cls            The class in which to find the declared method.
   * @param name           The simple name of the method.
   * @param parameterTypes The parameter array.
   * @return A Method object that reflects the specified declared method of the class or interface
   * represented by {@code cls} (excluding inherited methods), or {@code null} if the method is not
   * found.
   * @throws NullPointerException If {@code cls} or {@code name} is null.
   */
  public static Method getDeclaredMethod(final Class<?> cls, final String name,
      final Class<?>... parameterTypes) {
    final Method[] methods = cls.getDeclaredMethods();
    for (final Method method : methods) {
      if (name.equals(method.getName()) && Arrays
          .equals(method.getParameterTypes(), parameterTypes)) {
        return method;
      }
    }

    return null;
  }

  /**
   * Returns a Method object that reflects the specified declared method of the class or interface
   * represented by {@code cls} (including inherited methods), or {@code null} if the method is not
   * found.
   * <p>
   * Declared methods include public, protected, default (package) access, and private visibility.
   * <p>
   * The {@code name} parameter is a String that specifies the simple name of the desired method,
   * and the {@code parameterTypes} parameter is an array of Class objects that identify the
   * method's formal parameter types, in declared order. If more than one method with the same
   * parameter types is declared in a class, and one of these methods has a return type that is more
   * specific than any of the others, that method is returned; otherwise one of the methods is
   * chosen arbitrarily. If the name is {@code "<init>"} or {@code "<clinit>"} this method returns
   * {@code null}. If this Class object represents an array type, then this method does not find the
   * clone() method.
   * <p>
   * This method differentiates itself from {@link Class#getDeclaredMethod(String, Class...)} by
   * returning {@code null} when a method is not found, instead of throwing {@link
   * NoSuchMethodException}.
   *
   * @param cls            The class in which to find the declared method.
   * @param name           The simple name of the method.
   * @param parameterTypes The parameter array.
   * @return A Method object that reflects the specified declared method of the class or interface
   * represented by {@code cls} (including inherited methods), or {@code null} if the method is not
   * found.
   * @throws NullPointerException If {@code cls} or {@code name} is null.
   */
  public static Method getDeclaredMethodDeep(Class<?> cls, final String name,
      final Class<?>... parameterTypes) {
    Method method;
    do {
      method = getDeclaredMethod(cls, name, parameterTypes);
    }
    while (method == null && (cls = cls.getSuperclass()) != null);
    return method;
  }

  private Classes() {
  }
}
