package datadog.trace.agent.tooling;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.BOOTSTRAP_CLASSLOADER;
import static datadog.trace.bootstrap.WeakMap.Provider.newWeakMap;

import datadog.trace.bootstrap.WeakMap;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.SecureClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  private Map<TypeDescription, byte[]> helperMap = null;
  private final WeakMap<ClassLoader, Boolean> injectedClassLoaders = newWeakMap();

  // Neither Module nor WeakReference implements equals or hashcode so using a list
  private final List<WeakReference<Object>> helperModules = new ArrayList<>();

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
    this.helperMap = new LinkedHashMap<>(helperClassNames.size());
    for (final String helperName : helperClassNames) {
      final TypeDescription typeDesc =
          new TypeDescription.Latent(
              helperName, 0, null, Collections.<TypeDescription.Generic>emptyList());
      this.helperMap.put(typeDesc, helperMap.get(helperName));
    }
  }

  public static HelperInjector forDynamicTypes(final Collection<DynamicType.Unloaded<?>> helpers) {
    final Map<String, byte[]> bytes = new HashMap<>(helpers.size());
    for (final DynamicType.Unloaded<?> helper : helpers) {
      bytes.put(helper.getTypeDescription().getName(), helper.getBytes());
    }
    return new HelperInjector(bytes);
  }

  private synchronized Map<TypeDescription, byte[]> getHelperMap() throws IOException {
    if (helperMap == null) {
      helperMap = new LinkedHashMap<>(helperClassNames.size());
      for (final String helperName : helperClassNames) {
        final ClassFileLocator locator =
            ClassFileLocator.ForClassLoader.of(Utils.getAgentClassLoader());
        final byte[] classBytes = locator.locate(helperName).resolve();
        final TypeDescription typeDesc =
            new TypeDescription.Latent(
                helperName, 0, null, Collections.<TypeDescription.Generic>emptyList());
        helperMap.put(typeDesc, classBytes);
      }
    }
    return helperMap;
  }

  @Override
  public DynamicType.Builder<?> transform(
      final DynamicType.Builder<?> builder,
      final TypeDescription typeDescription,
      ClassLoader classLoader,
      final JavaModule module) {
    if (!helperClassNames.isEmpty()) {
      synchronized (this) {
        if (classLoader == BOOTSTRAP_CLASSLOADER) {
          classLoader = BOOTSTRAP_CLASSLOADER_PLACEHOLDER;
        }
        if (!injectedClassLoaders.containsKey(classLoader)) {
          try {
            final Map<TypeDescription, byte[]> helperMap = getHelperMap();
            log.debug("Injecting classes onto classloader {} -> {}", classLoader, helperClassNames);
            if (classLoader == BOOTSTRAP_CLASSLOADER_PLACEHOLDER) {
              final Map<TypeDescription, Class<?>> injected =
                  ClassInjector.UsingInstrumentation.of(
                          new File(System.getProperty("java.io.tmpdir")),
                          ClassInjector.UsingInstrumentation.Target.BOOTSTRAP,
                          AgentInstaller.getInstrumentation())
                      .inject(helperMap);
              for (final TypeDescription desc : injected.keySet()) {
                final Class<?> injectedClass =
                    Class.forName(desc.getName(), false, Utils.getBootstrapProxy());
                if (JavaModule.isSupported()) {
                  helperModules.add(new WeakReference<>(JavaModule.ofType(injectedClass).unwrap()));
                }
              }
            } else {
              final Map<TypeDescription, Class<?>> classMap =
                  new ClassInjector.UsingReflection(classLoader).inject(helperMap);
              if (JavaModule.isSupported()) {
                for (final Class<?> injectedClass : classMap.values()) {
                  helperModules.add(new WeakReference<>(JavaModule.ofType(injectedClass).unwrap()));
                }
              }
            }
          } catch (final Exception e) {
            log.error(
                "Error preparing helpers for "
                    + typeDescription
                    + ". Failed to inject helper classes into instance "
                    + classLoader
                    + " of type "
                    + (classLoader == BOOTSTRAP_CLASSLOADER_PLACEHOLDER
                        ? "<bootstrap>"
                        : classLoader.getClass().getName()),
                e);
            throw new RuntimeException(e);
          }
          injectedClassLoaders.put(classLoader, true);
        }

        ensureModuleCanReadHelperModules(module);
      }
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
            target.addReads(AgentInstaller.getInstrumentation(), helperModule);
          }
        }
      }
    }
  }
}
