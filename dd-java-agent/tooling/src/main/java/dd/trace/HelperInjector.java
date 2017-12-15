package dd.trace;

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

  /**
   * Construct HelperInjector.
   *
   * @param helperClassNames binary names of the helper classes to inject. These class names must be
   *     resolvable by the classloader returned by dd.trace.DDAdvice#getAgentClassLoader()
   */
  public HelperInjector(String... helperClassNames) {
    this.helperClassNames = new HashSet<String>(Arrays.asList(helperClassNames));
  }

  private synchronized Map<TypeDescription, byte[]> getHelperMap() throws ClassNotFoundException {
    if (helperMap == null) {
      helperMap = new HashMap<TypeDescription, byte[]>(helperClassNames.size());
      for (String helperName : helperClassNames) {
        Class<?> helper = DDAdvice.getAgentClassLoader().loadClass(helperName);
        helperMap.put(
            new TypeDescription.ForLoadedType(helper),
            ClassFileLocator.ForClassLoader.read(helper).resolve());
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
      try {
        new ClassInjector.UsingReflection(classLoader).inject(getHelperMap());
      } catch (ClassNotFoundException cnfe) {
        log.error("Failed to inject helper classes into " + classLoader, cnfe);
        throw new RuntimeException(cnfe);
      }
    }
    return builder;
  }
}
