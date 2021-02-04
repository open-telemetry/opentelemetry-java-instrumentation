/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static io.opentelemetry.javaagent.instrumentation.api.WeakMap.Provider.newWeakMap;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.BOOTSTRAP_CLASSLOADER;

import io.opentelemetry.javaagent.bootstrap.HelperResources;
import io.opentelemetry.javaagent.instrumentation.api.WeakMap;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
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
import java.util.concurrent.CopyOnWriteArrayList;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.utility.JavaModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Injects instrumentation helper classes into the user's classloader. */
public class HelperInjector implements Transformer {

  private static final Logger log = LoggerFactory.getLogger(HelperInjector.class);

  // Need this because we can't put null into the injectedClassLoaders map.
  private static final ClassLoader BOOTSTRAP_CLASSLOADER_PLACEHOLDER =
      new SecureClassLoader(null) {
        @Override
        public String toString() {
          return "<bootstrap>";
        }
      };

  private static final WeakMap<Class<?>, Boolean> injectedClasses = newWeakMap();

  private final String requestingName;

  private final Set<String> helperClassNames;
  private final Set<String> helperResourceNames;
  private final Map<String, byte[]> dynamicTypeMap = new LinkedHashMap<>();

  private final WeakMap<ClassLoader, Boolean> injectedClassLoaders = newWeakMap();

  private final List<WeakReference<Object>> helperModules = new CopyOnWriteArrayList<>();

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
      String requestingName, List<String> helperClassNames, List<String> helperResourceNames) {
    this.requestingName = requestingName;

    this.helperClassNames = new LinkedHashSet<>(helperClassNames);
    this.helperResourceNames = new LinkedHashSet<>(helperResourceNames);
  }

  public HelperInjector(String requestingName, Map<String, byte[]> helperMap) {
    this.requestingName = requestingName;

    helperClassNames = helperMap.keySet();
    dynamicTypeMap.putAll(helperMap);

    helperResourceNames = Collections.emptySet();
  }

  public static HelperInjector forDynamicTypes(
      String requestingName, Collection<DynamicType.Unloaded<?>> helpers) {
    Map<String, byte[]> bytes = new HashMap<>(helpers.size());
    for (DynamicType.Unloaded<?> helper : helpers) {
      bytes.put(helper.getTypeDescription().getName(), helper.getBytes());
    }
    return new HelperInjector(requestingName, bytes);
  }

  private Map<String, byte[]> getHelperMap() throws IOException {
    if (dynamicTypeMap.isEmpty()) {
      Map<String, byte[]> classnameToBytes = new LinkedHashMap<>();

      ClassFileLocator locator = ClassFileLocator.ForClassLoader.of(Utils.getAgentClassLoader());

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
      if (classLoader == BOOTSTRAP_CLASSLOADER) {
        classLoader = BOOTSTRAP_CLASSLOADER_PLACEHOLDER;
      }

      if (!injectedClassLoaders.containsKey(classLoader)) {
        try {
          log.debug("Injecting classes onto classloader {} -> {}", classLoader, helperClassNames);

          Map<String, byte[]> classnameToBytes = getHelperMap();
          Map<String, Class<?>> classes;
          if (classLoader == BOOTSTRAP_CLASSLOADER_PLACEHOLDER) {
            classes = injectBootstrapClassLoader(classnameToBytes);
          } else {
            classes = injectClassLoader(classLoader, classnameToBytes);
          }

          classes.values().forEach(c -> injectedClasses.put(c, Boolean.TRUE));

          // All agent helper classes are in the unnamed module
          // And there's exactly one unnamed module per classloader
          // Use the module of the first class for convenience
          if (JavaModule.isSupported()) {
            JavaModule javaModule = JavaModule.ofType(classes.values().iterator().next());
            helperModules.add(new WeakReference<>(javaModule.unwrap()));
          }
        } catch (Exception e) {
          if (log.isErrorEnabled()) {
            log.error(
                "Error preparing helpers while processing {} for {}. Failed to inject helper classes into instance {}",
                typeDescription,
                requestingName,
                classLoader,
                e);
          }
          throw new RuntimeException(e);
        }

        injectedClassLoaders.put(classLoader, true);
      }

      ensureModuleCanReadHelperModules(module);
    }

    if (!helperResourceNames.isEmpty()) {
      for (String resourceName : helperResourceNames) {
        URL resource = Utils.getAgentClassLoader().getResource(resourceName);
        if (resource == null) {
          log.debug("Helper resource {} requested but not found.", resourceName);
          continue;
        }

        log.debug("Injecting resource onto classloader {} -> {}", classLoader, resourceName);
        HelperResources.register(classLoader, resourceName, resource);
      }
    }

    return builder;
  }

  private Map<String, Class<?>> injectBootstrapClassLoader(Map<String, byte[]> classnameToBytes)
      throws IOException {
    // Mar 2020: Since we're proactively cleaning up tempDirs, we cannot share dirs per thread.
    // If this proves expensive, we could do a per-process tempDir with
    // a reference count -- but for now, starting simple.

    // Failures to create a tempDir are propagated as IOException and handled by transform
    File tempDir = createTempDir();
    try {
      return ClassInjector.UsingInstrumentation.of(
              tempDir,
              ClassInjector.UsingInstrumentation.Target.BOOTSTRAP,
              AgentInstaller.getInstrumentation())
          .injectRaw(classnameToBytes);
    } finally {
      // Delete fails silently
      deleteTempDir(tempDir);
    }
  }

  private Map<String, Class<?>> injectClassLoader(
      ClassLoader classLoader, Map<String, byte[]> classnameToBytes) {
    return new ClassInjector.UsingReflection(classLoader).injectRaw(classnameToBytes);
  }

  private void ensureModuleCanReadHelperModules(JavaModule target) {
    if (JavaModule.isSupported() && target != JavaModule.UNSUPPORTED && target.isNamed()) {
      for (WeakReference<Object> helperModuleReference : helperModules) {
        Object realModule = helperModuleReference.get();
        if (realModule != null) {
          JavaModule helperModule = JavaModule.of(realModule);

          if (!target.canRead(helperModule)) {
            log.debug("Adding module read from {} to {}", target, helperModule);
            target.modify(
                AgentInstaller.getInstrumentation(),
                Collections.singleton(helperModule),
                Collections.<String, Set<JavaModule>>emptyMap(),
                Collections.<String, Set<JavaModule>>emptyMap(),
                Collections.<Class<?>>emptySet(),
                Collections.<Class<?>, List<Class<?>>>emptyMap());
          }
        }
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

  public static boolean isInjectedClass(Class<?> c) {
    return injectedClasses.containsKey(c);
  }
}
