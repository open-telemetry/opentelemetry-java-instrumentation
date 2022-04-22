/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.SEVERE;

import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.javaagent.bootstrap.HelperResources;
import io.opentelemetry.javaagent.bootstrap.InjectedClassHelper;
import io.opentelemetry.javaagent.tooling.muzzle.HelperResource;
import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.nio.file.Files;
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
import javax.annotation.Nullable;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.utility.JavaModule;

/**
 * Injects instrumentation helper classes into the user's classloader.
 *
 * <p>Care must be taken when using this class. It is used both by the javaagent during its runtime
 * and by gradle muzzle verification plugin during build time. And some code paths in this class
 * require the usage of {@link Instrumentation}, which is available for the former, but not for the
 * latter. Unfortunately, these two "modes of operations" and not easily discernible just by reading
 * source code. Be careful.
 *
 * <p>In a nutshell, an instance of {@link Instrumentation} is needed for class injection into
 * bootstrap classloader. This should NOT happen during build-time muzzle verification phase.
 */
public class HelperInjector implements Transformer {

  private static final TransformSafeLogger logger =
      TransformSafeLogger.getLogger(HelperInjector.class);

  // a hook for static instrumentation used to save additional classes created by the agent
  // see https://github.com/open-telemetry/opentelemetry-java-contrib/tree/main/static-instrumenter
  public static StaticInstrumenterHook staticInstrumenterHook;

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
  private final Map<String, byte[]> dynamicTypeMap = new LinkedHashMap<>();

  private final Cache<ClassLoader, Boolean> injectedClassLoaders = Cache.weak();
  private final Cache<ClassLoader, Boolean> resourcesInjectedClassLoaders = Cache.weak();

  /**
   * Construct HelperInjector.
   *
   * @param helperClassNames binary names of the helper classes to inject. These class names must be
   *     resolvable by the classloader returned by
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
      // TODO can this be replaced with the context classloader?
      ClassLoader helpersSource,
      Instrumentation instrumentation) {
    this.requestingName = requestingName;

    this.helperClassNames = new LinkedHashSet<>(helperClassNames);
    this.helperResources = helperResources;
    this.helpersSource = helpersSource;
    this.instrumentation = instrumentation;
  }

  private HelperInjector(
      String requestingName, Map<String, byte[]> helperMap, Instrumentation instrumentation) {
    this.requestingName = requestingName;

    this.helperClassNames = helperMap.keySet();
    this.dynamicTypeMap.putAll(helperMap);

    this.helperResources = Collections.emptyList();
    this.helpersSource = null;
    this.instrumentation = instrumentation;
  }

  public static HelperInjector forDynamicTypes(
      String requestingName,
      Collection<DynamicType.Unloaded<?>> helpers,
      Instrumentation instrumentation) {
    Map<String, byte[]> bytes = new HashMap<>(helpers.size());
    for (DynamicType.Unloaded<?> helper : helpers) {
      bytes.put(helper.getTypeDescription().getName(), helper.getBytes());
    }
    return new HelperInjector(requestingName, bytes, instrumentation);
  }

  private Map<String, byte[]> getHelperMap() throws IOException {
    if (dynamicTypeMap.isEmpty()) {
      Map<String, byte[]> classnameToBytes = new LinkedHashMap<>();

      ClassFileLocator locator = ClassFileLocator.ForClassLoader.of(helpersSource);

      for (String helperClassName : helperClassNames) {
        byte[] classBytes = locator.locate(helperClassName).resolve();
        classnameToBytes.put(helperClassName, classBytes);
      }

      return classnameToBytes;
    } else {
      return dynamicTypeMap;
    }
  }

  @Override
  public DynamicType.Builder<?> transform(
      DynamicType.Builder<?> builder,
      TypeDescription typeDescription,
      ClassLoader classLoader,
      JavaModule module) {
    if (!helperClassNames.isEmpty()) {
      injectHelperClasses(typeDescription, classLoader, module);
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
                    "Injecting resources onto classloader {0} -> {1}",
                    new Object[] {classLoader, helperResource.getApplicationPath()});
              }
              HelperResources.register(classLoader, helperResource.getApplicationPath(), resources);
            }
          }

          return true;
        });
  }

  private void injectHelperClasses(
      TypeDescription typeDescription, ClassLoader classLoader, JavaModule module) {
    classLoader = maskNullClassLoader(classLoader);
    if (classLoader == BOOTSTRAP_CLASSLOADER_PLACEHOLDER && instrumentation == null) {
      logger.log(
          SEVERE,
          "Cannot inject helpers into bootstrap classloader without an instance of Instrumentation. Programmer error!");
      return;
    }

    injectedClassLoaders.computeIfAbsent(
        classLoader,
        cl -> {
          try {
            if (logger.isLoggable(FINE)) {
              logger.log(
                  FINE,
                  "Injecting classes onto classloader {0} -> {1}",
                  new Object[] {cl, helperClassNames});
            }

            Map<String, byte[]> classnameToBytes = getHelperMap();
            Map<String, HelperClassInjector> map =
                helperInjectors.computeIfAbsent(cl, (unused) -> new ConcurrentHashMap<>());
            for (Map.Entry<String, byte[]> entry : classnameToBytes.entrySet()) {
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

  private Map<String, Class<?>> injectBootstrapClassLoader(Map<String, byte[]> classnameToBytes)
      throws IOException {

    if (staticInstrumenterHook != null) {
      staticInstrumenterHook.injectClasses(classnameToBytes);
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
    private final byte[] bytes;

    HelperClassInjector(byte[] bytes) {
      this.bytes = bytes;
    }

    Class<?> inject(ClassLoader classLoader, String className) {
      Map<String, Class<?>> result =
          new ClassInjector.UsingReflection(classLoader)
              .injectRaw(Collections.singletonMap(className, bytes));
      return result.get(className);
    }
  }
}
