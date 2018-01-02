package dd.trace;

import com.datadoghq.agent.Utils;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
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
  private final Set<ClassLoader> injectedClassLoaders = new HashSet<ClassLoader>();

  /**
   * Construct HelperInjector.
   *
   * @param helperClassNames binary names of the helper classes to inject. These class names must be
   *     resolvable by the classloader returned by dd.trace.DDAdvice#getAgentClassLoader()
   */
  public HelperInjector(String... helperClassNames) {
    this.helperClassNames = new HashSet<String>(Arrays.asList(helperClassNames));
  }

  private synchronized Map<TypeDescription, byte[]> getHelperMap() throws IOException {
    if (helperMap == null) {
      helperMap = new HashMap<TypeDescription, byte[]>(helperClassNames.size());
      for (String helperName : helperClassNames) {
        final ClassFileLocator locator =
            ClassFileLocator.ForClassLoader.of(Utils.getAgentClassLoader());
        final byte[] classBytes = locator.locate(helperName).resolve();
        final TypeDescription typeDesc = new TypeDescription.Latent(helperName, 0, null);
        helperMap.put(typeDesc, classBytes);
      }
    }
    return helperMap;
  }

  @Override
  public DynamicType.Builder<?> transform(
      DynamicType.Builder<?> builder,
      TypeDescription typeDescription,
      ClassLoader classLoader,
      JavaModule module) {
    if (helperClassNames.size() > 0 && classLoader != null) {
      synchronized (this) {
        if (!injectedClassLoaders.contains(classLoader)) {
          try {
            new ClassInjector.UsingReflection(classLoader).inject(getHelperMap());
          } catch (Exception e) {
            log.error("Failed to inject helper classes into " + classLoader, e);
            throw new RuntimeException(e);
          }
          injectedClassLoaders.add(classLoader);
        }
      }
    }
    return builder;
  }
}
