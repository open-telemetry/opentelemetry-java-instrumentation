/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static java.util.logging.Level.FINE;

import io.opentelemetry.sdk.autoconfigure.spi.Ordered;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.logging.Logger;

public final class SafeServiceLoader {

  private static final Logger logger = Logger.getLogger(SafeServiceLoader.class.getName());

  /**
   * Delegates to {@link ServiceLoader#load(Class, ClassLoader)} and then eagerly iterates over
   * returned {@code Iterable}, ignoring any potential {@link UnsupportedClassVersionError}.
   *
   * <p>Those errors can happen when some classes returned by {@code ServiceLoader} were compiled
   * for later java version than is used by currently running JVM. During normal course of business
   * this should not happen. Please read CONTRIBUTING.md, section "Testing - Java versions" for a
   * background info why this is Ok.
   */
  // Because we want to catch exception per iteration
  @SuppressWarnings("ForEachIterable")
  public static <T> List<T> load(Class<T> serviceClass) {
    List<T> result = new ArrayList<>();
    ServiceLoader<T> services = ServiceLoader.load(serviceClass);
    for (Iterator<T> iterator = new SafeIterator<>(services.iterator()); iterator.hasNext(); ) {
      T service = iterator.next();
      if (service != null) {
        result.add(service);
      }
    }
    return result;
  }

  /**
   * Same as {@link #load(Class)}, but also orders the returned implementations by comparing their
   * {@link Ordered#order()}.
   */
  public static <T extends Ordered> List<T> loadOrdered(Class<T> serviceClass) {
    List<T> result = load(serviceClass);
    result.sort(Comparator.comparing(Ordered::order));
    return result;
  }

  private SafeServiceLoader() {}

  private static class SafeIterator<T> implements Iterator<T> {
    private final Iterator<T> delegate;

    SafeIterator(Iterator<T> iterator) {
      delegate = iterator;
    }

    private static void handleUnsupportedClassVersionError(
        UnsupportedClassVersionError unsupportedClassVersionError) {
      logger.log(
          FINE,
          "Unable to load instrumentation class: {0}",
          unsupportedClassVersionError.getMessage());
    }

    @Override
    public boolean hasNext() {
      // jdk9 and newer throw UnsupportedClassVersionError in hasNext()
      while (true) {
        try {
          return delegate.hasNext();
        } catch (UnsupportedClassVersionError unsupportedClassVersionError) {
          handleUnsupportedClassVersionError(unsupportedClassVersionError);
        }
      }
    }

    @Override
    public T next() {
      // jdk8 throws UnsupportedClassVersionError in next()
      try {
        return delegate.next();
      } catch (UnsupportedClassVersionError unsupportedClassVersionError) {
        handleUnsupportedClassVersionError(unsupportedClassVersionError);
        return null;
      }
    }
  }
}
