package io.opentelemetry.auto.bootstrap.instrumentation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Utils {
  /**
   * Delegates to {@link ServiceLoader#load(Class, ClassLoader)} and then eagerly
   * iterates over returned {@code Iterable}, ignoring any potential {@link UnsupportedClassVersionError}.
   * <p>
   * Those errors can happen when some classes returned by {@code ServiceLoader} were compiled
   * for later java version than is used by currently running JVM. During normal course of business
   * this should not happen. Please read CONTRIBUTING.md, section "Testing - Java versions" for
   * a background info why this is Ok.
   */
  public static <T> Iterable<T> safeLoadServices(Class<T> serviceClass, ClassLoader classLoader) {
    List<T> result = new ArrayList<>();
    ServiceLoader<T> services = ServiceLoader.load(serviceClass, classLoader);
    for (Iterator<T> iter = services.iterator(); iter.hasNext(); ) {
      try {
        result.add(iter.next());
      } catch (java.lang.UnsupportedClassVersionError e) {
        log.error("Unable to load instrumentation class", e);

      }
    }
    return result;
  }
}
