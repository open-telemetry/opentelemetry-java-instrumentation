package datadog.trace.agent.tooling;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.BOOTSTRAP_CLASSLOADER;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
  private final Set<String> helperClassNames;
  private Map<TypeDescription, byte[]> helperMap = null;
  private final Set<ClassLoader> injectedClassLoaders = new HashSet<>();

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
      final ClassLoader classLoader,
      final JavaModule module) {
    if (helperClassNames.size() > 0) {
      synchronized (this) {
        if (!injectedClassLoaders.contains(classLoader)) {
          try {
            final Map<TypeDescription, byte[]> helperMap = getHelperMap();
            log.debug("Injecting classes onto classloader {} -> {}", classLoader, helperClassNames);
            if (classLoader == BOOTSTRAP_CLASSLOADER) {
              final Map<TypeDescription, Class<?>> injected =
                  ClassInjector.UsingInstrumentation.of(
                          new File(System.getProperty("java.io.tmpdir")),
                          ClassInjector.UsingInstrumentation.Target.BOOTSTRAP,
                          AgentInstaller.getInstrumentation())
                      .inject(helperMap);
              for (final TypeDescription desc : injected.keySet()) {
                Class.forName(desc.getName(), false, Utils.getBootstrapProxy());
              }
            } else {
              new ClassInjector.UsingReflection(classLoader).inject(helperMap);
            }
          } catch (final Exception e) {
            log.error(
                "Error preparing helpers for "
                    + typeDescription
                    + ". Failed to inject helper classes into instance "
                    + classLoader
                    + " of type "
                    + (classLoader == BOOTSTRAP_CLASSLOADER
                        ? "<bootstrap>"
                        : classLoader.getClass().getName()),
                e);
            throw new RuntimeException(e);
          }
          injectedClassLoaders.add(classLoader);
        }
      }
    }
    return builder;
  }
}
