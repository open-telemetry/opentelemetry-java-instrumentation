/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;

import io.opentelemetry.javaagent.bootstrap.DefineClassHelper.Handler;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import org.objectweb.asm.ClassReader;

public class DefineClassHandler implements Handler {
  public static final DefineClassHandler INSTANCE = new DefineClassHandler();

  private final DefineClassContextImpl rootContext = new DefineClassContextImpl(null, null, null);
  private final ThreadLocal<DefineClassContextImpl> defineClassContext =
      ThreadLocal.withInitial(() -> rootContext);

  private static Predicate<ClassLoader> ignoredClassLoaders = (classLoader) -> false;

  // visible for testing
  DefineClassHandler() {}

  static void setIgnoredClassLoadersPredicate(Predicate<ClassLoader> predicate) {
    ignoredClassLoaders = predicate;
  }

  @Override
  @Nullable
  public DefineClassContext beforeDefineClass(
      ClassLoader classLoader, String className, byte[] classBytes, int offset, int length) {
    // with OpenJ9 class data sharing we don't get real class bytes
    if (classBytes == null
        || (classBytes.length == 40
            && new String(classBytes, ISO_8859_1).startsWith("J9ROMCLASSCOOKIE"))) {
      return null;
    }

    // skip ignore class loaders, class is not going to be instrumented anyway
    if (ignoredClassLoaders.test(classLoader)) {
      return null;
    }

    // when an instrumented defineClass delegates to another instrumented defineClass we can skip
    // processing on the second call
    if (isDefining(classLoader, className)) {
      return null;
    }

    Set<String> superNames = new HashSet<>();
    DefineClassContextImpl context = enter(classLoader, className);
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
    } catch (Throwable ignored) {
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
    DefineClassContextImpl context = enter();
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
  public void afterDefineClass(@Nullable DefineClassContext context) {
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
    DefineClassContextImpl context = INSTANCE.defineClassContext.get();
    return context.failedClassDotName != null && context.failedClassDotName.equals(dotClassName);
  }

  public static Set<String> getSuperTypes() {
    Set<String> superNames = INSTANCE.defineClassContext.get().superDotNames;
    return superNames == null ? emptySet() : superNames;
  }

  private DefineClassContextImpl enter() {
    return enter(null, null);
  }

  private DefineClassContextImpl enter(
      @Nullable ClassLoader classLoader, @Nullable String className) {
    DefineClassContextImpl previous = defineClassContext.get();
    DefineClassContextImpl context = new DefineClassContextImpl(previous, classLoader, className);
    defineClassContext.set(context);
    return context;
  }

  // visible for testing
  boolean isDefining(ClassLoader classLoader, String className) {
    DefineClassContextImpl current = defineClassContext.get();
    return current.classLoader == classLoader
        && current.className != null
        && current.className.equals(className);
  }

  private class DefineClassContextImpl implements DefineClassContext {
    @Nullable private final DefineClassContextImpl previous;
    @Nullable private final ClassLoader classLoader;
    @Nullable private final String className;
    @Nullable String failedClassDotName;
    @Nullable Set<String> superDotNames;

    private DefineClassContextImpl(
        @Nullable DefineClassContextImpl previous,
        @Nullable ClassLoader classLoader,
        @Nullable String className) {
      this.previous = previous;
      this.classLoader = classLoader;
      this.className = className;
    }

    @Override
    public void exit() {
      // ROOT itself has no previous context but is never returned to callers. Every returned
      // context is created by enter(), which captures ROOT or an outer context as its predecessor.
      defineClassContext.set(requireNonNull(previous));
    }
  }
}
