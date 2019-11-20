package datadog.trace.instrumentation.jaxrs1;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class OverriddenMethodIterable implements Iterable<Method> {
  private final Class<?> baseClass;
  private final Method baseMethod;

  public OverriddenMethodIterable(final Class target, final Method baseMethod) {
    baseClass = target;
    this.baseMethod = baseMethod;
  }

  @Override
  public Iterator<Method> iterator() {
    return new MethodIterator();
  }

  public class MethodIterator implements Iterator<Method> {
    private Method next = baseMethod;
    private final Set<Class<?>> queuedInterfaces = new HashSet<>();
    private final Queue<Class<?>> classesToCheck = new LinkedList<>();

    public MethodIterator() {
      final List<Class<?>> currentInterfaces = Arrays.asList(baseClass.getInterfaces());
      queuedInterfaces.addAll(currentInterfaces);
      classesToCheck.addAll(currentInterfaces);

      final Class<?> superclass = baseClass.getSuperclass();
      if (superclass != null) {
        classesToCheck.add(superclass);
      }
    }

    @Override
    public boolean hasNext() {
      if (next == null) {
        calculateNext();
      }
      return next != null;
    }

    @Override
    public Method next() {
      final Method next = this.next;
      this.next = null;
      return next;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }

    private void calculateNext() {
      while (next == null && !classesToCheck.isEmpty()) {
        final Class currentClass = classesToCheck.remove();
        queueNewInterfaces(currentClass.getInterfaces());

        final Class<?> superClass = currentClass.getSuperclass();
        if (superClass != null) {
          classesToCheck.add(currentClass.getSuperclass());
        }

        next = findMatchingMethod(currentClass.getDeclaredMethods());
      }
    }

    private Method findMatchingMethod(final Method[] methods) {
      nextMethod:
      for (final Method method : methods) {
        if (!baseMethod.getReturnType().equals(method.getReturnType())) {
          continue;
        }

        if (!baseMethod.getName().equals(method.getName())) {
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

    private void queueNewInterfaces(final Class[] interfaces) {
      for (final Class clazz : interfaces) {
        if (queuedInterfaces.add(clazz)) {
          classesToCheck.add(clazz);
        }
      }
    }
  }
}
