/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import io.opentelemetry.javaagent.tooling.BytecodeWithUrl;
import io.opentelemetry.javaagent.tooling.muzzle.InstrumentationModuleMuzzle;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.StringMatcher;

/**
 * Class loader used to load the helper classes from {@link
 * io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule}s, so that those
 * classes have access to both the agent/extension classes and the instrumented application classes.
 *
 * <p>This class loader implements the following classloading delegation strategy:
 *
 * <ul>
 *   <li>First, injected classes are considered (usually the helper classes from the
 *       InstrumentationModule)
 *   <li>Next, the class loader looks in the agent or extension class loader, depending on where the
 *       InstrumentationModule comes from
 *   <li>Finally, the instrumented application class loader is checked for the class
 * </ul>
 *
 * <p>In addition, this class loader ensures that the lookup of corresponding .class resources
 * follow the same delegation strategy, so that bytecode inspection tools work correctly.
 */
public class InstrumentationModuleClassLoader extends ClassLoader {

  static {
    ClassLoader.registerAsParallelCapable();
  }

  private static final ClassLoader BOOT_LOADER = new ClassLoader() {};

  private static final Map<String, BytecodeWithUrl> ALWAYS_INJECTED_CLASSES =
      Collections.singletonMap(
          LookupExposer.class.getName(), BytecodeWithUrl.create(LookupExposer.class).cached());
  private static final ProtectionDomain PROTECTION_DOMAIN = getProtectionDomain();
  private static final MethodHandle FIND_PACKAGE_METHOD = getFindPackageMethod();

  private final Map<String, BytecodeWithUrl> additionalInjectedClasses;
  private final ClassLoader agentOrExtensionCl;
  private volatile MethodHandles.Lookup cachedLookup;

  @Nullable private final ClassLoader instrumentedCl;

  /**
   * Only class names matching this matcher will be attempted to be loaded from the {@link
   * #agentOrExtensionCl}. If a class is requested and it does not match this matcher, the lookup in
   * {@link #agentOrExtensionCl} will be skipped.
   */
  private final ElementMatcher<String> agentClassNamesMatcher;

  /**
   * Mutable set of packages from the agent classloader to hide. So even if a class matches {@link
   * #agentClassNamesMatcher}, it will not be attempted to be loaded from the agent classloader if
   * it is from any of these packages.
   */
  private final Set<String> hiddenAgentPackages;

  private final Set<InstrumentationModule> installedModules;

  public InstrumentationModuleClassLoader(
      ClassLoader instrumentedCl, ClassLoader agentOrExtensionCl) {
    this(
        instrumentedCl,
        agentOrExtensionCl,
        new StringMatcher("io.opentelemetry.javaagent", StringMatcher.Mode.STARTS_WITH));
  }

  InstrumentationModuleClassLoader(
      @Nullable ClassLoader instrumentedCl,
      ClassLoader agentOrExtensionCl,
      ElementMatcher<String> classesToLoadFromAgentOrExtensionCl) {
    // agent/extension-class loader is "main"-parent, but class lookup is overridden
    super(agentOrExtensionCl);
    additionalInjectedClasses = new ConcurrentHashMap<>();
    installedModules = Collections.newSetFromMap(new ConcurrentHashMap<>());
    this.agentOrExtensionCl = agentOrExtensionCl;
    this.instrumentedCl = instrumentedCl;
    this.agentClassNamesMatcher = classesToLoadFromAgentOrExtensionCl;
    this.hiddenAgentPackages = Collections.newSetFromMap(new ConcurrentHashMap<>());
  }

  /**
   * Provides a Lookup within this class loader. See {@link LookupExposer} for the details.
   *
   * @return a lookup capable of accessing public types in this class loader
   */
  public MethodHandles.Lookup getLookup() {
    if (cachedLookup == null) {
      // Load the injected copy of LookupExposer and invoke it
      try {
        MethodType getLookupType = MethodType.methodType(MethodHandles.Lookup.class);
        // we don't mind the race condition causing the initialization to run multiple times here
        Class<?> lookupExposer = loadClass(LookupExposer.class.getName());
        // Note: we must use MethodHandles instead of reflection here to avoid a recursion
        // for our internal ReflectionInstrumentationModule which instruments reflection methods
        cachedLookup =
            (MethodHandles.Lookup)
                MethodHandles.publicLookup()
                    .findStatic(lookupExposer, "getLookup", getLookupType)
                    .invoke();
      } catch (Throwable e) {
        throw new IllegalStateException(e);
      }
    }
    return cachedLookup;
  }

  public synchronized void installModule(InstrumentationModule module) {
    if (module.getClass().getClassLoader() != agentOrExtensionCl) {
      throw new IllegalArgumentException(
          module.getClass().getName() + " is not loaded by " + agentOrExtensionCl);
    }
    if (!installedModules.add(module)) {
      return;
    }
    Map<String, BytecodeWithUrl> classesToInject =
        getClassesToInject(module).stream()
            .collect(
                Collectors.toMap(
                    className -> className,
                    className -> BytecodeWithUrl.create(className, agentOrExtensionCl)));
    installInjectedClasses(classesToInject);
    if (module instanceof ExperimentalInstrumentationModule) {
      hiddenAgentPackages.addAll(
          ((ExperimentalInstrumentationModule) module).agentPackagesToHide());
    }
  }

  public synchronized boolean hasModuleInstalled(InstrumentationModule module) {
    return installedModules.contains(module);
  }

  // Visible for testing
  synchronized void installInjectedClasses(Map<String, BytecodeWithUrl> classesToInject) {
    classesToInject.forEach(additionalInjectedClasses::putIfAbsent);
  }

  private static Set<String> getClassesToInject(InstrumentationModule module) {
    Set<String> toInject = new HashSet<>(InstrumentationModuleMuzzle.getHelperClassNames(module));
    // TODO (Jonas): Make muzzle include advice classes as helper classes
    // so that we don't have to include them here
    toInject.addAll(getModuleAdviceNames(module));
    if (module instanceof ExperimentalInstrumentationModule) {
      toInject.removeAll(((ExperimentalInstrumentationModule) module).injectedClassNames());
    }
    return toInject;
  }

  private static Set<String> getModuleAdviceNames(InstrumentationModule module) {
    Set<String> adviceNames = new HashSet<>();
    TypeTransformer nameCollector =
        new TypeTransformer() {
          @Override
          public void applyAdviceToMethod(
              ElementMatcher<? super MethodDescription> methodMatcher, String adviceClassName) {
            adviceNames.add(adviceClassName);
          }

          @Override
          public void applyTransformer(AgentBuilder.Transformer transformer) {}
        };
    for (TypeInstrumentation instr : module.typeInstrumentations()) {
      instr.transform(nameCollector);
    }
    return adviceNames;
  }

  public static final Map<String, byte[]> bytecodeOverride = new ConcurrentHashMap<>();

  @Override
  public Class<?> loadClass(String name) throws ClassNotFoundException {
    // We explicitly override loadClass from ClassLoader to ensure
    // that loadClass is properly excluded from our internal ClassLoader Instrumentations
    // (e.g. LoadInjectedClassInstrumentation, BooDelegationInstrumentation)
    // Otherwise this will cause recursion in invokedynamic linkage
    return loadClass(name, false);
  }

  @Override
  @SuppressWarnings("removal") // AccessController is deprecated for removal
  protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    synchronized (getClassLoadingLock(name)) {
      Class<?> result = findLoadedClass(name);

      // This CL is self-first: Injected class are loaded BEFORE a parent lookup
      if (result == null) {
        BytecodeWithUrl injected = getInjectedClass(name);
        if (injected != null) {
          byte[] bytecode =
              bytecodeOverride.get(name) != null
                  ? bytecodeOverride.get(name)
                  : injected.getBytecode();
          if (System.getSecurityManager() == null) {
            result = defineClassWithPackage(name, bytecode);
          } else {
            result =
                java.security.AccessController.doPrivileged(
                    (PrivilegedAction<Class<?>>) () -> defineClassWithPackage(name, bytecode));
          }
        }
      }
      if (result == null && shouldLoadFromAgent(name)) {
        result = tryLoad(agentOrExtensionCl, name);
      }
      if (result == null) {
        result = tryLoad(instrumentedCl, name);
      }

      if (result != null) {
        if (resolve) {
          resolveClass(result);
        }
        return result;
      } else {
        throw new ClassNotFoundException(name);
      }
    }
  }

  private boolean shouldLoadFromAgent(String dotClassName) {
    if (!agentClassNamesMatcher.matches(dotClassName)) {
      return false;
    }
    for (String packageName : hiddenAgentPackages) {
      if (dotClassName.startsWith(packageName)) {
        return false;
      }
    }
    return true;
  }

  private static Class<?> tryLoad(@Nullable ClassLoader cl, String name) {
    try {
      return Class.forName(name, false, cl);
    } catch (ClassNotFoundException e) {
      return null;
    }
  }

  @Override
  public URL getResource(String resourceName) {
    String className = resourceToClassName(resourceName);
    if (className == null) {
      // delegate to just the default parent (the agent class loader)
      return super.getResource(resourceName);
    }
    // for classes use the same precedence as in loadClass
    BytecodeWithUrl injected = getInjectedClass(className);
    if (injected != null) {
      return injected.getUrl();
    }
    URL fromAgentCl = agentOrExtensionCl.getResource(resourceName);
    if (fromAgentCl != null) {
      return fromAgentCl;
    }

    if (instrumentedCl != null) {
      return instrumentedCl.getResource(resourceName);
    } else {
      return BOOT_LOADER.getResource(resourceName);
    }
  }

  @Override
  public Enumeration<URL> getResources(String resourceName) throws IOException {
    String className = resourceToClassName(resourceName);
    if (className == null) {
      return super.getResources(resourceName);
    }
    URL resource = getResource(resourceName);
    List<URL> result =
        resource != null ? Collections.singletonList(resource) : Collections.emptyList();
    return Collections.enumeration(result);
  }

  @Nullable
  private static String resourceToClassName(String resourceName) {
    if (!resourceName.endsWith(".class")) {
      return null;
    }
    String className = resourceName;
    if (className.startsWith("/")) {
      className = className.substring(1);
    }
    className = className.replace('/', '.');
    className = className.substring(0, className.length() - ".class".length());
    return className;
  }

  @Nullable
  private BytecodeWithUrl getInjectedClass(String name) {
    BytecodeWithUrl alwaysInjected = ALWAYS_INJECTED_CLASSES.get(name);
    if (alwaysInjected != null) {
      return alwaysInjected;
    }
    return additionalInjectedClasses.get(name);
  }

  private Class<?> defineClassWithPackage(String name, byte[] bytecode) {
    try {
      int lastDotIndex = name.lastIndexOf('.');
      if (lastDotIndex != -1) {
        String packageName = name.substring(0, lastDotIndex);
        safeDefinePackage(packageName);
      }
      return defineClass(name, bytecode, 0, bytecode.length, PROTECTION_DOMAIN);
    } catch (LinkageError error) {
      // Precaution against linkage error due to nested instrumentations happening
      // it might be possible that e.g. an advice class has already been defined
      // during an instrumentation of defineClass
      Class<?> clazz = findLoadedClass(name);
      if (clazz != null) {
        return clazz;
      }
      throw error;
    }
  }

  private void safeDefinePackage(String packageName) {
    if (findPackage(packageName) == null) {
      try {
        definePackage(packageName, null, null, null, null, null, null, null);
      } catch (IllegalArgumentException e) {
        // Can happen if two classes from the same package are loaded concurrently
        if (findPackage(packageName) == null) {
          // package still doesn't exist, the IllegalArgumentException must be for a different
          // reason than a race condition
          throw e;
        }
      }
    }
  }

  /**
   * Invokes {@link #getPackage(String)} for Java 8 and {@link #getDefinedPackage(String)} for Java
   * 9+.
   *
   * <p>Package-private for testing.
   *
   * @param name the name of the package find
   * @return the found package or null if it was not found.
   */
  @SuppressWarnings({"deprecation", "InvalidLink"})
  Package findPackage(String name) {
    try {
      return (Package) FIND_PACKAGE_METHOD.invoke(this, name);
    } catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  @SuppressWarnings("removal") // AccessController is deprecated for removal
  private static ProtectionDomain getProtectionDomain() {
    if (System.getSecurityManager() == null) {
      return InstrumentationModuleClassLoader.class.getProtectionDomain();
    }
    return java.security.AccessController.doPrivileged(
        (PrivilegedAction<ProtectionDomain>)
            ((Class<?>) InstrumentationModuleClassLoader.class)::getProtectionDomain);
  }

  private static MethodHandle getFindPackageMethod() {
    MethodType methodType = MethodType.methodType(Package.class, String.class);
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    try {
      return lookup.findVirtual(ClassLoader.class, "getDefinedPackage", methodType);
    } catch (NoSuchMethodException | IllegalAccessException e) {
      // In Java 8 getDefinedPackage does not exist (HotSpot) or is not accessible (OpenJ9)
      try {
        return lookup.findVirtual(ClassLoader.class, "getPackage", methodType);
      } catch (NoSuchMethodException ex) {
        throw new IllegalStateException("expected method to always exist!", ex);
      } catch (IllegalAccessException ex2) {
        throw new IllegalStateException("Method should be accessible from here", ex2);
      }
    }
  }
}
