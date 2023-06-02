/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import io.opentelemetry.javaagent.bootstrap.DefineClassHelper.Handler;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.objectweb.asm.ClassReader;

public class DefineClassHandler implements Handler {
  public static final DefineClassHandler INSTANCE = new DefineClassHandler();
  private static final ThreadLocal<DefineClassContextImpl> defineClassContext =
      ThreadLocal.withInitial(() -> DefineClassContextImpl.NOP);

  private DefineClassHandler() {}

  @Override
  public DefineClassContext beforeDefineClass(
      ClassLoader classLoader, String className, byte[] classBytes, int offset, int length) {
    // with OpenJ9 class data sharing we don't get real class bytes
    if (classBytes == null
        || (classBytes.length == 40
            && new String(classBytes, StandardCharsets.ISO_8859_1)
                .startsWith("J9ROMCLASSCOOKIE"))) {
      return null;
    }

    Set<String> superNames = new HashSet<>();
    DefineClassContextImpl context = DefineClassContextImpl.enter();
    // attempt to load super types of currently loaded class
    // for a class to be loaded all of its super types must be loaded, here we just change the order
    // of operations and load super types before transforming the bytes for current class so that
    // we could use these super types for resolving the advice that needs to be applied to current
    // class
    try {
      ClassReader cr = new ClassReader(classBytes, offset, length);
      String superName = cr.getSuperName();
      if (superName != null) {
        String superDotName = superName.replace('/', '.');
        Class<?> clazz = Class.forName(superDotName, false, classLoader);
        addSuperNames(superNames, clazz);
      }
      String[] interfaces = cr.getInterfaces();
      for (String interfaceName : interfaces) {
        String interfaceDotName = interfaceName.replace('/', '.');
        Class<?> clazz = Class.forName(interfaceDotName, false, classLoader);
        addSuperNames(superNames, clazz);
      }
      context.superDotNames = superNames;
    } catch (Throwable throwable) {
      // loading of super class or interface failed
      // mark current class as failed to skip matching and transforming it
      // we'll let defining the class proceed as usual so that it would throw the same exception as
      // it does when running without the agent
      context.failedClassDotName = className;
    }

    return context;
  }

  @Override
  public DefineClassContext beforeDefineLambdaClass(Class<?> lambdaInterface) {
    DefineClassContextImpl context = DefineClassContextImpl.enter();
    Set<String> superNames = new HashSet<>();
    addSuperNames(superNames, lambdaInterface);
    context.superDotNames = superNames;

    return context;
  }

  private static void addSuperNames(Set<String> superNames, Class<?> clazz) {
    if (clazz == null || !superNames.add(clazz.getName())) {
      return;
    }
    addSuperNames(superNames, clazz.getSuperclass());
    for (Class<?> interfaceClass : clazz.getInterfaces()) {
      addSuperNames(superNames, interfaceClass);
    }
  }

  @Override
  public void afterDefineClass(DefineClassContext context) {
    if (context != null) {
      context.exit();
    }
  }

  /**
   * Detect whether loading the specified class is known to fail.
   *
   * @param dotClassName class being loaded
   * @return true if it is known that loading class with given name will fail
   */
  public static boolean isFailedClass(String dotClassName) {
    DefineClassContextImpl context = defineClassContext.get();
    return context.failedClassDotName != null && context.failedClassDotName.equals(dotClassName);
  }

  public static Set<String> getSuperTypes() {
    Set<String> superNames = defineClassContext.get().superDotNames;
    return superNames == null ? Collections.emptySet() : superNames;
  }

  private static class DefineClassContextImpl implements DefineClassContext {
    private static final DefineClassContextImpl NOP = new DefineClassContextImpl();

    private final DefineClassContextImpl previous;
    String failedClassDotName;
    Set<String> superDotNames;

    private DefineClassContextImpl() {
      previous = null;
    }

    private DefineClassContextImpl(DefineClassContextImpl previous) {
      this.previous = previous;
    }

    static DefineClassContextImpl enter() {
      DefineClassContextImpl previous = defineClassContext.get();
      DefineClassContextImpl context = new DefineClassContextImpl(previous);
      defineClassContext.set(context);
      return context;
    }

    @Override
    public void exit() {
      defineClassContext.set(previous);
    }
  }
}
