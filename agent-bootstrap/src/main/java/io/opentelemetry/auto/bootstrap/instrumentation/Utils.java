package io.opentelemetry.auto.bootstrap.instrumentation;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Utils {
  public static <T> Iterable<T> safeLoadServices(Class<T> serviceClass, ClassLoader classLoader) {
    ServiceLoader<T> instrumenters = ServiceLoader.load(serviceClass, classLoader);
    final Iterator<T> iterator = instrumenters.iterator();
    return new Iterable<T>() {
      @Override
      public Iterator<T> iterator() {
        return new Iterator<T>() {
          T next = null;

          @Override
          public boolean hasNext() {
            next = tryNext();
            return next != null;
          }

          @Override
          public T next() {
            if (next != null) {
              return next;
            }
            next = tryNext();
            if (next == null) {
              throw new NoSuchElementException();
            }
            return next;
          }

          @Override
          public void remove() {
            iterator.remove();
          }

          private T tryNext() {
            if (!iterator.hasNext()) {
              return null;
            }
            try {
              return iterator.next();
            } catch (java.lang.UnsupportedClassVersionError e) {
              log.error("Unable to load instrumentation class", e);
              return tryNext();
            }
          }
        };
      }
    };
  }
}
