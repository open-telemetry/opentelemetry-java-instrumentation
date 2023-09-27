/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.SEVERE;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.javaagent.bootstrap.HelperResources;
import io.opentelemetry.javaagent.bootstrap.InjectedClassHelper;
import io.opentelemetry.javaagent.tooling.muzzle.HelperResource;
import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.nio.file.Files;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.SecureClassLoader;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.utility.JavaModule;

/**
 * Injects instrumentation helper classes into the user's class loader.
 *
 * <p>Care must be taken when using this class. It is used both by the javaagent during its runtime
 * and by gradle muzzle verification plugin during build time. And some code paths in this class
 * require the usage of {@link Instrumentation}, which is available for the former, but not for the
 * latter. Unfortunately, these two "modes of operations" and not easily discernible just by reading
 * source code. Be careful.
 *
 * <p>In a nutshell, an instance of {@link Instrumentation} is needed for class injection into the
 * bootstrap class loader. This should NOT happen during build-time muzzle verification phase.
 */
public class HelperInjector implements Transformer {

  private static final TransformSafeLogger logger =
      TransformSafeLogger.getLogger(HelperInjector.class);

  private static final ProtectionDomain PROTECTION_DOMAIN =
      HelperInjector.class.getProtectionDomain();

  // a hook for static instrumentation used to save additional classes created by the agent
  // see https://github.com/open-telemetry/opentelemetry-java-contrib/tree/main/static-instrumenter
  private static volatile HelperInjectorListener helperInjectorListener;

  static {
    InjectedClassHelper.internalSetHelperClassDetector(HelperInjector::isInjectedClass);
    InjectedClassHelper.internalSetHelperClassLoader(HelperInjector::loadHelperClass);
  }

  // Need this because we can't put null into the injectedClassLoaders map.
  private static final ClassLoader BOOTSTRAP_CLASSLOADER_PLACEHOLDER =
      new SecureClassLoader(null) {
        @Override
        public String toString() {
          return "<bootstrap>";
        }
      };

  private static final HelperClassInjector BOOT_CLASS_INJECTOR =
      new HelperClassInjector(null) {
        @Override
        Class<?> inject(ClassLoader classLoader, String className) {
          throw new UnsupportedOperationException("should not be called");
        }
      };

  private static final Cache<ClassLoader, Map<String, HelperClassInjector>> helperInjectors =
      Cache.weak();

  private final String requestingName;

  private final Set<String> helperClassNames;
  private final List<HelperResource> helperResources;
  @Nullable private final ClassLoader helpersSource;
  @Nullable private final Instrumentation instrumentation;
  private final Map<String, Function<ClassLoader, byte[]>> dynamicTypeMap = new LinkedHashMap<>();

  private final Cache<ClassLoader, Boolean> injectedClassLoaders = Cache.weak();
  private final Cache<ClassLoader, Boolean> resourcesInjectedClassLoaders = Cache.weak();

  /**
   * Construct HelperInjector.
   *
   * @param helperClassNames binary names of the helper classes to inject. These class names must be
   *     resolvable by the class loader returned by
   *     io.opentelemetry.javaagent.tooling.Utils#getAgentClassLoader(). Classes are injected in the
   *     order provided. This is important if there is interdependency between helper classes that
   *     requires them to be injected in a specific order. And be careful, the class's package in
   *     library will be renamed like 'io.opentelemetry.instrumentation' to
   *     'io.opentelemetry.javaagent.shaded.instrumentation'
   */
  public HelperInjector(
      String requestingName,
      List<String> helperClassNames,
      List<HelperResource> helperResources,
      ClassLoader helpersSource,
      Instrumentation instrumentation) {
    this.requestingName = requestingName;

    this.helperClassNames = new LinkedHashSet<>(helperClassNames);
    this.helperResources = helperResources;
    this.helpersSource = helpersSource;
    this.instrumentation = instrumentation;
  }

  public HelperInjector(
      String requestingName,
      Map<String, Function<ClassLoader, byte[]>> helperMap,
      List<HelperResource> helperResources,
      ClassLoader helpersSource,
      Instrumentation instrumentation) {
    this.requestingName = requestingName;

    this.helperClassNames = helperMap.keySet();
    this.dynamicTypeMap.putAll(helperMap);

    this.helperResources = helperResources;
    this.helpersSource = helpersSource;
    this.instrumentation = instrumentation;
  }

  public static HelperInjector forDynamicTypes(
      String requestingName,
      Collection<DynamicType.Unloaded<?>> helpers,
      Instrumentation instrumentation) {
    Map<String, Function<ClassLoader, byte[]>> bytes = new HashMap<>(helpers.size());
    for (DynamicType.Unloaded<?> helper : helpers) {
      bytes.put(helper.getTypeDescription().getName(), cl -> helper.getBytes());
    }
    return new HelperInjector(
        requestingName, bytes, Collections.emptyList(), null, instrumentation);
  }

  public static void setHelperInjectorListener(HelperInjectorListener listener) {
    helperInjectorListener = listener;
  }

  private Map<String, Supplier<byte[]>> getHelperMap(ClassLoader targetClassloader) {
    Map<String, Supplier<byte[]>> result = new LinkedHashMap<>();
    if (dynamicTypeMap.isEmpty()) {

      for (String helperClassName : helperClassNames) {
        result.put(
            helperClassName,
            () -> {
              try (ClassFileLocator locator = ClassFileLocator.ForClassLoader.of(helpersSource)) {
                return locator.locate(helperClassName).resolve();
              } catch (IOException exception) {
                if (logger.isLoggable(SEVERE)) {
                  logger.log(
                      SEVERE, "Failed to read {0}", new Object[] {helperClassName}, exception);
                }
                throw new IllegalStateException("Failed to read " + helperClassName, exception);
              }
            });
      }
    } else {
      dynamicTypeMap.forEach(
          (name, bytecodeGenerator) -> {
            // Eagerly compute bytecode to not risk accidentally holding onto targetClassloader for
            // too long
            byte[] bytecode = bytecodeGenerator.apply(targetClassloader);
            result.put(name, () -> bytecode);
          });
    }

    return result;
  }

  @Override
  @CanIgnoreReturnValue
  public DynamicType.Builder<?> transform(
      DynamicType.Builder<?> builder,
      TypeDescription typeDescription,
      ClassLoader classLoader,
      JavaModule javaModule,
      ProtectionDomain protectionDomain) {
    if (!helperClassNames.isEmpty()) {
      injectHelperClasses(typeDescription, classLoader);
    }

    if (classLoader != null && helpersSource != null && !helperResources.isEmpty()) {
      injectHelperResources(classLoader);
    }

    return builder;
  }

  private void injectHelperResources(ClassLoader classLoader) {
    resourcesInjectedClassLoaders.computeIfAbsent(
        classLoader,
        cl -> {
          for (HelperResource helperResource : helperResources) {
            List<URL> resources;
            try {
              resources =
                  Collections.list(helpersSource.getResources(helperResource.getAgentPath()));
            } catch (IOException e) {
              logger.log(
                  SEVERE,
                  "Unexpected exception occurred when loading resources {}; skipping",
                  new Object[] {helperResource.getAgentPath()},
                  e);
              continue;
            }
            if (resources.isEmpty()) {
              logger.log(
                  FINE,
                  "Helper resources {0} requested but not found.",
                  helperResource.getAgentPath());
              continue;
            }

            if (helperResource.allClassLoaders()) {
              logger.log(
                  FINE,
                  "Injecting resources onto all classloaders: {0}",
                  helperResource.getApplicationPath());
              HelperResources.registerForAllClassLoaders(
                  helperResource.getApplicationPath(), resources);
            } else {
              if (logger.isLoggable(FINE)) {
                logger.log(
                    FINE,
                    "Injecting resources onto class loader {0} -> {1}",
                    new Object[] {classLoader, helperResource.getApplicationPath()});
              }
              HelperResources.register(classLoader, helperResource.getApplicationPath(), resources);
            }
          }

          return true;
        });
  }

  private void injectHelperClasses(TypeDescription typeDescription, ClassLoader classLoader) {
    classLoader = maskNullClassLoader(classLoader);
    if (classLoader == BOOTSTRAP_CLASSLOADER_PLACEHOLDER && instrumentation == null) {
      logger.log(
          SEVERE,
          "Cannot inject helpers into the bootstrap class loader without an instance of Instrumentation. Programmer error!");
      return;
    }

    injectedClassLoaders.computeIfAbsent(
        classLoader,
        cl -> {
          try {
            if (logger.isLoggable(FINE)) {
              logger.log(
                  FINE,
                  "Injecting classes onto class loader {0} -> {1}",
                  new Object[] {cl, helperClassNames});
            }

            Map<String, Supplier<byte[]>> classnameToBytes = getHelperMap(cl);
            Map<String, HelperClassInjector> map =
                helperInjectors.computeIfAbsent(cl, (unused) -> new ConcurrentHashMap<>());
            for (Map.Entry<String, Supplier<byte[]>> entry : classnameToBytes.entrySet()) {
              // for boot loader we use a placeholder injector, we only need these classes to be
              // in the injected classes map to later tell which of the classes are injected
              HelperClassInjector injector =
                  isBootClassLoader(cl)
                      ? BOOT_CLASS_INJECTOR
                      : new HelperClassInjector(entry.getValue());
              map.put(entry.getKey(), injector);
            }

            // For boot loader we define the classes immediately. For other loaders we load them
            // from the loadClass method of the class loader.
            if (isBootClassLoader(cl)) {
              injectBootstrapClassLoader(classnameToBytes);
            }
          } catch (Exception e) {
            if (logger.isLoggable(SEVERE)) {
              logger.log(
                  SEVERE,
                  "Error preparing helpers while processing {0} for {1}. Failed to inject helper classes into instance {2}",
                  new Object[] {typeDescription, requestingName, cl},
                  e);
            }
            throw new IllegalStateException(e);
          }
          return true;
        });
  }

  private static Map<String, byte[]> resolve(Map<String, Supplier<byte[]>> classes) {
    Map<String, byte[]> result = new LinkedHashMap<>();
    for (Map.Entry<String, Supplier<byte[]>> entry : classes.entrySet()) {
      result.put(entry.getKey(), entry.getValue().get());
    }
    return result;
  }

  private Map<String, Class<?>> injectBootstrapClassLoader(Map<String, Supplier<byte[]>> inject)
      throws IOException {

    Map<String, byte[]> classnameToBytes = resolve(inject);
    if (helperInjectorListener != null) {
      helperInjectorListener.onInjection(classnameToBytes);
    }

    if (ClassInjector.UsingUnsafe.isAvailable()) {
      return ClassInjector.UsingUnsafe.ofBootLoader().injectRaw(classnameToBytes);
    }

    // Mar 2020: Since we're proactively cleaning up tempDirs, we cannot share dirs per thread.
    // If this proves expensive, we could do a per-process tempDir with
    // a reference count -- but for now, starting simple.

    // Failures to create a tempDir are propagated as IOException and handled by transform
    File tempDir = createTempDir();
    try {
      return ClassInjector.UsingInstrumentation.of(
              tempDir, ClassInjector.UsingInstrumentation.Target.BOOTSTRAP, instrumentation)
          .injectRaw(classnameToBytes);
    } finally {
      // Delete fails silently
      deleteTempDir(tempDir);
    }
  }

  private static File createTempDir() throws IOException {
    return Files.createTempDirectory("opentelemetry-temp-jars").toFile();
  }

  private static void deleteTempDir(File file) {
    // Not using Files.delete for deleting the directory because failures
    // create Exceptions which may prove expensive.  Instead using the
    // older File API which simply returns a boolean.
    boolean deleted = file.delete();
    if (!deleted) {
      file.deleteOnExit();
    }
  }

  private static ClassLoader maskNullClassLoader(ClassLoader classLoader) {
    return classLoader != null ? classLoader : BOOTSTRAP_CLASSLOADER_PLACEHOLDER;
  }

  private static boolean isBootClassLoader(ClassLoader classLoader) {
    return classLoader == BOOTSTRAP_CLASSLOADER_PLACEHOLDER;
  }

  public static boolean isInjectedClass(Class<?> clazz) {
    return isInjectedClass(clazz.getClassLoader(), clazz.getName());
  }

  public static boolean isInjectedClass(ClassLoader classLoader, String className) {
    Map<String, HelperClassInjector> injectorMap =
        helperInjectors.get(maskNullClassLoader(classLoader));
    if (injectorMap == null) {
      return false;
    }
    return injectorMap.containsKey(className);
  }

  public static Class<?> loadHelperClass(ClassLoader classLoader, String className) {
    if (classLoader == null) {
      throw new IllegalStateException("boot loader not supported");
    }
    Map<String, HelperClassInjector> injectorMap = helperInjectors.get(classLoader);
    if (injectorMap == null) {
      return null;
    }
    HelperClassInjector helperClassInjector = injectorMap.get(className);
    if (helperClassInjector == null) {
      return null;
    }
    return helperClassInjector.inject(classLoader, className);
  }

  private static class HelperClassInjector {
    private final Supplier<byte[]> bytes;

    HelperClassInjector(Supplier<byte[]> bytes) {
      this.bytes = bytes;
    }

    Class<?> inject(ClassLoader classLoader, String className) {
      // if security manager is present byte buddy calls
      // checkPermission(new ReflectPermission("suppressAccessChecks")) so we must call class
      // injection with AccessController.doPrivileged when security manager is enabled
      Map<String, Class<?>> result =
          execute(
              () ->
                  new ClassInjector.UsingReflection(classLoader, PROTECTION_DOMAIN)
                      .injectRaw(Collections.singletonMap(className, bytes.get())));
      return result.get(className);
    }
  }

  @SuppressWarnings("removal") // AccessController is deprecated for removal
  private static <T> T execute(PrivilegedAction<T> action) {
    if (System.getSecurityManager() != null) {
      return java.security.AccessController.doPrivileged(action);
    } else {
      return action.run();
    }
  }
}
