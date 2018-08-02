package datadog.trace.agent.tooling;

import java.util.ArrayList;
import java.util.List;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.utility.JavaModule;

/**
 * Locate resources with the loading classloader. If the loading classloader cannot find the desired
 * resource, check up the classloader hierarchy until a resource is found or the bootstrap loader is
 * reached.
 */
public class DDLocationStrategy implements AgentBuilder.LocationStrategy {
  public ClassFileLocator classFileLocator(ClassLoader classLoader) {
    return classFileLocator(classLoader, null);
  }

  @Override
  public ClassFileLocator classFileLocator(ClassLoader classLoader, final JavaModule javaModule) {
    final List<ClassFileLocator> locators = new ArrayList<>();
    while (classLoader != null) {
      locators.add(ClassFileLocator.ForClassLoader.of(classLoader));
      classLoader = classLoader.getParent();
    }
    locators.add(ClassFileLocator.ForClassLoader.of(Utils.getBootstrapProxy()));
    return new ClassFileLocator.Compound(locators.toArray(new ClassFileLocator[0]));
  }
}
