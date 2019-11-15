package datadog.trace.agent.tooling;

import java.util.ArrayList;
import java.util.List;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.utility.JavaModule;

/**
 * Locate resources with the loading classloader. Because of a quirk with the way classes appended
 * to the bootstrap classpath work, we first check our bootstrap proxy. If the loading classloader
 * cannot find the desired resource, check up the classloader hierarchy until a resource is found.
 */
public class DDLocationStrategy implements AgentBuilder.LocationStrategy {
  public ClassFileLocator classFileLocator(final ClassLoader classLoader) {
    return classFileLocator(classLoader, null);
  }

  @Override
  public ClassFileLocator classFileLocator(ClassLoader classLoader, final JavaModule javaModule) {
    final List<ClassFileLocator> locators = new ArrayList<>();
    locators.add(ClassFileLocator.ForClassLoader.of(Utils.getBootstrapProxy()));
    while (classLoader != null) {
      locators.add(ClassFileLocator.ForClassLoader.WeaklyReferenced.of(classLoader));
      classLoader = classLoader.getParent();
    }
    return new ClassFileLocator.Compound(locators.toArray(new ClassFileLocator[0]));
  }
}
