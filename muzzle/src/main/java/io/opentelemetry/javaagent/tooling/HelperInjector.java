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
import io.opentelemetry.javaagent.bootstrap.InjectedClassHelper.HelperClassLoader;
import io.opentelemetry.javaagent.bootstrap.field.VirtualFieldAccessorMarker;
import io.opentelemetry.javaagent.bootstrap.field.VirtualFieldDetector;
import io.opentelemetry.javaagent.extension.instrumentation.internal.injection.InjectionMode;
import io.opentelemetry.javaagent.tooling.muzzle.HelperResource;
import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.nio.file.Files;
import java.security.ProtectionDomain;
import java.security.SecureClassLoader;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.description.type.TypeDescription;
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
        Class<?> inject(
            ClassLoader classLoader, String className, ClassLoaderAccess classLoaderAccess) {
          throw new UnsupportedOperationException("should not be called");
        }
      };

  private static final Cache<ClassLoader, Map<String, HelperClassInjector>> helperInjectors =
      Cache.weak();

  private final String requestingName;

  private final Function<ClassLoader, List<HelperClassDefinition>> helperClassesGenerator;
  private final List<HelperResource> helperResources;
  @Nullable private final ClassLoader helpersSource;
  @Nullable private final Instrumentation instrumentation;

  private final Cache<ClassLoader, Boolean> injectedClassLoaders = Cache.weak();

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

    List<HelperClassDefinition> helpers =
        helperClassNames.stream()
            .distinct()
            .map(
                className ->
                    HelperClassDefinition.create(
                        className, helpersSource, InjectionMode.CLASS_ONLY))
            .collect(Collectors.toList());

    this.helperClassesGenerator = (cl) -> helpers;
    this.helperResources = helperResources;
    this.helpersSource = helpersSource;
    this.instrumentation = instrumentation;
  }

  public HelperInjector(
      String requestingName,
      Function<ClassLoader, List<HelperClassDefinition>> helperClassesGenerators,
      List<HelperResource> helperResources,
      ClassLoader helpersSource,
      Instrumentation instrumentation) {
    this.requestingName = requestingName;

    this.helperClassesGenerator = helperClassesGenerators;
    this.helperResources = helperResources;
    this.helpersSource = helpersSource;
    this.instrumentation = instrumentation;
  }

  public static HelperInjector forDynamicTypes(
      String requestingName,
      Collection<DynamicType.Unloaded<?>> helpers,
      Instrumentation instrumentation) {

    List<HelperClassDefinition> helperDefinitions =
        helpers.stream()
            .map(helperType -> HelperClassDefinition.create(helperType, InjectionMode.CLASS_ONLY))
            .collect(Collectors.toList());

    return new HelperInjector(
        requestingName, cl -> helperDefinitions, Collections.emptyList(), null, instrumentation);
  }

  public static void setHelperInjectorListener(HelperInjectorListener listener) {
    helperInjectorListener = listener;
  }

  @Override
  @CanIgnoreReturnValue
  public DynamicType.Builder<?> transform(
      DynamicType.Builder<?> builder,
      TypeDescription typeDescription,
      ClassLoader classLoader,
      JavaModule javaModule,
      ProtectionDomain protectionDomain) {
    injectedClassLoaders.computeIfAbsent(
        maskNullClassLoader(classLoader),
        cl -> {
          List<HelperClassDefinition> helpers =
              helperClassesGenerator.apply(unmaskNullClassLoader(cl));

          LinkedHashMap<String, Supplier<byte[]>> classesToInject =
              helpers.stream()
                  .filter(helper -> helper.getInjectionMode().shouldInjectClass())
                  .collect(
                      Collectors.toMap(
                          HelperClassDefinition::getClassName,
                          helper -> () -> helper.getBytecode().getBytecode(),
                          (a, b) -> {
                            throw new IllegalStateException(
                                "Duplicate classnames for helper class detected!");
                          },
                          LinkedHashMap::new));

          Map<String, URL> classResourcesToInject =
              helpers.stream()
                  .filter(helper -> helper.getInjectionMode().shouldInjectResource())
                  .collect(
                      Collectors.toMap(
                          helper -> helper.getClassName().replace('.', '/') + ".class",
                          helper -> helper.getBytecode().getUrl()));

          injectHelperClasses(typeDescription, cl, classesToInject);
          if (!isBootClassLoader(cl)) {
            injectHelperResources(cl, classResourcesToInject);
          }
          return true;
        });
    return builder;
  }

  private void injectHelperResources(
      ClassLoader classLoader, Map<String, URL> additionalResources) {
    for (HelperResource helperResource : helperResources) {
      List<URL> resources;
      try {
        resources = Collections.list(helpersSource.getResources(helperResource.getAgentPath()));
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
            FINE, "Helper resources {0} requested but not found.", helperResource.getAgentPath());
        continue;
      }

      if (helperResource.allClassLoaders()) {
        logger.log(
            FINE,
            "Injecting resources onto all classloaders: {0}",
            helperResource.getApplicationPath());
        HelperResources.registerForAllClassLoaders(helperResource.getApplicationPath(), resources);
      } else {
        injectResourceToClassloader(classLoader, helperResource.getApplicationPath(), resources);
      }
    }
    additionalResources.forEach(
        (path, url) ->
            injectResourceToClassloader(classLoader, path, Collections.singletonList(url)));
  }

  private static void injectResourceToClassloader(
      ClassLoader classLoader, String path, List<URL> resources) {
    if (logger.isLoggable(FINE)) {
      logger.log(
          FINE,
          "Injecting resources onto class loader {0} -> {1}",
          new Object[] {classLoader, path});
    }
    HelperResources.register(classLoader, path, resources);
  }

  @SuppressWarnings("NonApiType")
  private void injectHelperClasses(
      TypeDescription typeDescription,
      ClassLoader classLoader,
      LinkedHashMap<String, Supplier<byte[]>> classnameToBytes) {
    if (classnameToBytes.isEmpty()) {
      return;
    }
    if (classLoader == BOOTSTRAP_CLASSLOADER_PLACEHOLDER && instrumentation == null) {
      logger.log(
          SEVERE,
          "Cannot inject helpers into the bootstrap class loader without an instance of Instrumentation. Programmer error!");
      return;
    }
    try {
      if (logger.isLoggable(FINE)) {
        logger.log(
            FINE,
            "Injecting classes onto class loader {0} -> {1}",
            new Object[] {classLoader, classnameToBytes.keySet()});
      }

      Map<String, HelperClassInjector> map =
          helperInjectors.computeIfAbsent(classLoader, (unused) -> new ConcurrentHashMap<>());
      for (Map.Entry<String, Supplier<byte[]>> entry : classnameToBytes.entrySet()) {
        // for boot loader we use a placeholder injector, we only need these classes to be
        // in the injected classes map to later tell which of the classes are injected
        HelperClassInjector injector =
            isBootClassLoader(classLoader)
                ? BOOT_CLASS_INJECTOR
                : new HelperClassInjector(entry.getValue());
        map.put(entry.getKey(), injector);
      }

      // For boot loader we define the classes immediately. For other loaders we load them
      // from the loadClass method of the class loader.
      if (isBootClassLoader(classLoader)) {
        injectBootstrapClassLoader(classnameToBytes);
      }
    } catch (Exception e) {
      if (logger.isLoggable(SEVERE)) {
        logger.log(
            SEVERE,
            "Error preparing helpers while processing {0} for {1}. Failed to inject helper classes into instance {2}",
            new Object[] {typeDescription, requestingName, classLoader},
            e);
      }
      throw new IllegalStateException(e);
    }
  }

  private static Map<String, byte[]> resolve(Map<String, Supplier<byte[]>> classes) {
    Map<String, byte[]> result = new LinkedHashMap<>();
    for (Map.Entry<String, Supplier<byte[]>> entry : classes.entrySet()) {
      result.put(entry.getKey(), entry.getValue().get());
    }
    return result;
  }

  private static final String virtualFieldPackage =
      VirtualFieldAccessorMarker.class.getPackage().getName() + ".";

  private void injectBootstrapClassLoader(Map<String, Supplier<byte[]>> inject) throws IOException {

    Map<String, byte[]> classnameToBytes = resolve(inject);
    if (helperInjectorListener != null) {
      helperInjectorListener.onInjection(classnameToBytes);
    }

    if (ClassInjector.UsingLookup.isAvailable()) {
      // because all generated virtual field classes are in the same package we can use lookup to
      // define them
      ClassInjector virtualFieldInjector =
          ClassInjector.UsingLookup.of(VirtualFieldDetector.lookup());

      for (Iterator<Map.Entry<String, byte[]>> iterator = classnameToBytes.entrySet().iterator();
          iterator.hasNext(); ) {
        Map.Entry<String, byte[]> entry = iterator.next();
        String className = entry.getKey();

        if (className.startsWith(virtualFieldPackage)) {
          iterator.remove();
          try {
            virtualFieldInjector.injectRaw(Collections.singletonMap(className, entry.getValue()));
          } catch (LinkageError error) {
            // Unlike the ClassInjector.UsingUnsafe.ofBootLoader() ClassInjector.UsingLookup doesn't
            // check whether the class got loaded when there is an exception defining it.
            // We attempt to define some classes multiple times and fail with LinkageError duplicate
            // class definition on the second attempt. We recover from this by checking whether the
            // class is loaded and if it is, we ignore the error.
            try {
              Class.forName(className, false, null);
            } catch (ClassNotFoundException unused) {
              // throw the original error
              throw error;
            }
          }
        }
      }
    }

    // TODO by default, we use unsafe to define rest of the classes into boot loader
    // can be disabled with -Dnet.bytebuddy.safe=true
    // use -Dsun.misc.unsafe.memory.access=debug to check where unsafe is used
    if (ClassInjector.UsingUnsafe.isAvailable() && !classnameToBytes.isEmpty()) {
      ClassInjector.UsingUnsafe.ofBootLoader().injectRaw(classnameToBytes);
      return;
    }

    // Mar 2020: Since we're proactively cleaning up tempDirs, we cannot share dirs per thread.
    // If this proves expensive, we could do a per-process tempDir with
    // a reference count -- but for now, starting simple.

    // Failures to create a tempDir are propagated as IOException and handled by transform
    if (!classnameToBytes.isEmpty()) {
      File tempDir = createTempDir();
      try {
        ClassInjector.UsingInstrumentation.of(
                tempDir, ClassInjector.UsingInstrumentation.Target.BOOTSTRAP, instrumentation)
            .injectRaw(classnameToBytes);
      } finally {
        // Delete fails silently
        deleteTempDir(tempDir);
      }
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

  private static ClassLoader unmaskNullClassLoader(ClassLoader classLoader) {
    return isBootClassLoader(classLoader) ? null : classLoader;
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

  private static final ClassValue<ClassLoaderAccess> classLoaderAccessClassValue =
      new ClassValue<ClassLoaderAccess>() {
        @Override
        protected ClassLoaderAccess computeValue(Class<?> type) {
          return new ClassLoaderAccess();
        }
      };

  private static class ClassLoaderAccess {
    volatile MethodHandle findLoadedClass;
    volatile MethodHandle defineClass;

    void initialize(MethodHandles.Lookup lookup) throws Exception {
      if (findLoadedClass == null) {
        findLoadedClass =
            lookup.findVirtual(
                ClassLoader.class,
                "findLoadedClass",
                MethodType.methodType(Class.class, String.class));
      }
      if (defineClass == null) {
        defineClass =
            lookup.findVirtual(
                ClassLoader.class,
                "defineClass",
                MethodType.methodType(
                    Class.class,
                    String.class,
                    byte[].class,
                    int.class,
                    int.class,
                    ProtectionDomain.class));
      }
    }

    Class<?> loadClass(ClassLoader classLoader, String className, byte[] classBytes)
        throws Throwable {
      // first check whether the class is already defined
      Class<?> clazz = (Class<?>) findLoadedClass.invoke(classLoader, className);
      if (clazz != null) {
        return clazz;
      }

      // we don't attempt to synchronize class definitions but let them race and recover from
      // duplicate class definitions by checking whether the class got defined in the catch block
      // we don't define packages for injected helper classes
      try {
        return (Class<?>)
            defineClass.invoke(
                classLoader,
                className,
                classBytes,
                0,
                classBytes.length,
                HelperInjector.PROTECTION_DOMAIN);
      } catch (Throwable throwable) {
        clazz = (Class<?>) findLoadedClass.invoke(classLoader, className);
        if (clazz != null) {
          return clazz;
        }
        throw throwable;
      }
    }
  }

  static HelperClassLoader loadHelperClass(ClassLoader classLoader, String className) {
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

    return lookup -> {
      ClassLoaderAccess classLoaderAccess = classLoaderAccessClassValue.get(classLoader.getClass());
      classLoaderAccess.initialize(lookup);
      return helperClassInjector.inject(classLoader, className, classLoaderAccess);
    };
  }

  private static class HelperClassInjector {
    private final Supplier<byte[]> bytes;

    HelperClassInjector(Supplier<byte[]> bytes) {
      this.bytes = bytes;
    }

    Class<?> inject(ClassLoader classLoader, String className, ClassLoaderAccess classLoaderAccess)
        throws Throwable {
      return classLoaderAccess.loadClass(classLoader, className, bytes.get());
    }
  }
}
