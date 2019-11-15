package datadog.trace.instrumentation.jaxrs1;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

public class OverriddenMethodIterable implements Iterable<Method> {
  private final Class baseClass;
  private final Method baseMethod;

  public OverriddenMethodIterable(final Class target, final Method baseMethod) {
    baseClass = target;
    this.baseMethod = baseMethod;
  }

  @Override
  public Iterator<Method> iterator() {
    return new Iterator<Method>() {
      private Method next = baseMethod;
      private Class<?> currentClass = baseClass;
      private boolean analyzeCurrentClass = !baseMethod.getDeclaringClass().equals(baseClass);
      private final Queue<Class<?>> interfaces = new LinkedList<>();

      @Override
      public boolean hasNext() {
        if (next == null) {
          calculateNext();
        }
        return next == null;
      }

      @Override
      public Method next() {
        final Method next = this.next;
        this.next = null;
        return next;
      }

      @Override
      public void remove() {
        // Ignore
      }

      private void calculateNext() {
        if (currentClass == null) {
          return;
        }
        if (analyzeCurrentClass) {
          next = findMatchingMethod(currentClass.getDeclaredMethods());
          if (next != null) {
            return;
          }
          interfaces.addAll(Arrays.asList(currentClass.getInterfaces()));
        }
        if (!interfaces.isEmpty()) {
          final Class<?> nextInterface = interfaces.remove();
          interfaces.addAll(Arrays.asList(nextInterface.getInterfaces()));
          next = findMatchingMethod(nextInterface.getDeclaredMethods());
          if (next != null) {
            return;
          }
        } else {
          currentClass = currentClass.getSuperclass();
          analyzeCurrentClass = true;
        }
      }

      private Method findMatchingMethod(final Method[] methods) {
        nextMethod:
        for (final Method method : methods) {
          if (!baseMethod.getName().equals(method.getName())) {
            continue;
          }
          if (!baseMethod.getReturnType().equals(method.getReturnType())) {
            continue;
          }
          final Class<?>[] baseParameterTypes = baseMethod.getParameterTypes();
          final Class<?>[] parameterTypes = method.getParameterTypes();
          if (baseParameterTypes.length != parameterTypes.length) {
            continue;
          }
          for (int i = 0; i < baseParameterTypes.length; i++) {
            if (!baseParameterTypes[i].equals(parameterTypes[i])) {
              continue nextMethod;
            }
          }
          return method;
        }
        return null;
      }
    };
  }
}
