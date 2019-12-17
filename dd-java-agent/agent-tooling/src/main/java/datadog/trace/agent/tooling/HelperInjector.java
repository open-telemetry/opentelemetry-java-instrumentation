package datadog.trace.agent.tooling;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.BOOTSTRAP_CLASSLOADER;
import static datadog.trace.bootstrap.WeakMap.Provider.newWeakMap;

import datadog.trace.bootstrap.WeakMap;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.SecureClassLoader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.utility.JavaModule;

/** Injects instrumentation helper classes into the user's classloader. */
@Slf4j
public class HelperInjector implements Transformer {
  // Need this because we can't put null into the injectedClassLoaders map.
  private static final ClassLoader BOOTSTRAP_CLASSLOADER_PLACEHOLDER =
      new SecureClassLoader(null) {};

  private final Set<String> helperClassNames;
  private final Map<String, byte[]> dynamicTypeMap = new LinkedHashMap<>();

  private final WeakMap<ClassLoader, Boolean> injectedClassLoaders = newWeakMap();

  private final List<WeakReference<Object>> helperModules = new CopyOnWriteArrayList<>();
  /**
   * Construct HelperInjector.
   *
   * @param helperClassNames binary names of the helper classes to inject. These class names must be
   *     resolvable by the classloader returned by
   *     datadog.trace.agent.tooling.Utils#getAgentClassLoader(). Classes are injected in the order
   *     provided. This is important if there is interdependency between helper classes that
   *     requires them to be injected in a specific order.
   */
  public HelperInjector(final String... helperClassNames) {
    this.helperClassNames = new LinkedHashSet<>(Arrays.asList(helperClassNames));
  }

  public HelperInjector(final Map<String, byte[]> helperMap) {
    helperClassNames = helperMap.keySet();
    dynamicTypeMap.putAll(helperMap);
  }

  public static HelperInjector forDynamicTypes(final Collection<DynamicType.Unloaded<?>> helpers) {
    final Map<String, byte[]> bytes = new HashMap<>(helpers.size());
    for (final DynamicType.Unloaded<?> helper : helpers) {
      bytes.put(helper.getTypeDescription().getName(), helper.getBytes());
    }
    return new HelperInjector(bytes);
  }

  private Map<String, byte[]> getHelperMap() throws IOException {
    if (dynamicTypeMap.isEmpty()) {
      final Map<String, byte[]> classnameToBytes = new LinkedHashMap<>();

      final ClassFileLocator locator =
          ClassFileLocator.ForClassLoader.of(Utils.getAgentClassLoader());

      for (final String helperClassName : helperClassNames) {
        final byte[] classBytes = locator.locate(helperClassName).resolve();
        classnameToBytes.put(helperClassName, classBytes);
      }

      return classnameToBytes;
    } else {
      return dynamicTypeMap;
    }
  }

  @Override
  public DynamicType.Builder<?> transform(
      final DynamicType.Builder<?> builder,
      final TypeDescription typeDescription,
      ClassLoader classLoader,
      final JavaModule module) {
    if (!helperClassNames.isEmpty()) {
      if (classLoader == BOOTSTRAP_CLASSLOADER) {
        classLoader = BOOTSTRAP_CLASSLOADER_PLACEHOLDER;
      }

      if (!injectedClassLoaders.containsKey(classLoader)) {
        try {
          log.debug("Injecting classes onto classloader {} -> {}", classLoader, helperClassNames);

          final Map<String, byte[]> classnameToBytes = getHelperMap();
          final Map<String, Class<?>> classes;
          if (classLoader == BOOTSTRAP_CLASSLOADER_PLACEHOLDER) {
            classes =
                ClassInjector.UsingInstrumentation.of(
                        new File(System.getProperty("java.io.tmpdir")),
                        ClassInjector.UsingInstrumentation.Target.BOOTSTRAP,
                        AgentInstaller.getInstrumentation())
                    .injectRaw(classnameToBytes);
          } else {
            classes = new ClassInjector.UsingReflection(classLoader).injectRaw(classnameToBytes);
          }

          // All datadog helper classes are in the unnamed module
          // And there's exactly one unnamed module per classloader
          // Use the module of the first class for convenience
          if (JavaModule.isSupported()) {
            final JavaModule javaModule = JavaModule.ofType(classes.values().iterator().next());
            helperModules.add(new WeakReference<>(javaModule.unwrap()));
          }
        } catch (final Exception e) {
          final String classLoaderType =
              classLoader == BOOTSTRAP_CLASSLOADER_PLACEHOLDER
                  ? "<bootstrap>"
                  : classLoader.getClass().getName();

          log.error(
              "Error preparing helpers for {}. Failed to inject helper classes into instance {} of type {}",
              typeDescription,
              classLoader,
              classLoaderType,
              e);
          throw new RuntimeException(e);
        }

        injectedClassLoaders.put(classLoader, true);
      }

      ensureModuleCanReadHelperModules(module);
    }
    return builder;
  }

  private void ensureModuleCanReadHelperModules(final JavaModule target) {
    if (JavaModule.isSupported() && target != JavaModule.UNSUPPORTED && target.isNamed()) {
      for (final WeakReference<Object> helperModuleReference : helperModules) {
        final Object realModule = helperModuleReference.get();
        if (realModule != null) {
          final JavaModule helperModule = JavaModule.of(realModule);

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
}
