package datadog.trace.instrumentation.osgi;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Constants;
import datadog.trace.agent.tooling.Instrumenter;
import java.security.ProtectionDomain;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;

@AutoService(Instrumenter.class)
public final class OSGIClassloadingInstrumentation extends Instrumenter.Default {
  public OSGIClassloadingInstrumentation() {
    super("osgi-classloading");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // OSGi Bundle class loads the system property which defines bootstrap classes
    return named("org.osgi.framework.Bundle").or(named("org.eclipse.osgi.launch.EquinoxFactory"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      Constants.class.getName(), OSGIClassloadingInstrumentation.class.getName() + "$Helper"
    };
  }

  @Override
  public void postMatch(
      final TypeDescription typeDescription,
      final ClassLoader classLoader,
      final JavaModule module,
      final Class<?> classBeingRedefined,
      final ProtectionDomain protectionDomain) {
    // Set the system prop to tell OSGi to delegate classloads for datadog bootstrap classes
    System.setProperty(
        Helper.PROPERTY_KEY, Helper.getNewValue(System.getProperty(Helper.PROPERTY_KEY)));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod().and(isPublic()).and(named("newFramework")).and(takesArgument(0, Map.class)),
        EquinoxFactoryAdvice.class.getName());
  }

  /**
   * Sometimes OSGi doesn't read configuration from system properties. Handle this case for {@code
   * EquinoxFactory}.
   */
  public static class EquinoxFactoryAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(@Advice.Argument(0) final Map<String, String> configuration) {
      if (configuration != null) {
        configuration.put(
            Helper.PROPERTY_KEY, Helper.getNewValue(configuration.get(Helper.PROPERTY_KEY)));
      }
    }
  }

  public static class Helper {

    public static final String PROPERTY_KEY = "org.osgi.framework.bootdelegation";
    public static final String PROPERTY_VALUE;

    static {
      // Set the config option to tell osgi to delegate classloads for datadog bootstrap classes
      final StringBuilder prefixes = new StringBuilder("");
      for (int i = 0; i < Constants.BOOTSTRAP_PACKAGE_PREFIXES.length; ++i) {
        if (i > 0) {
          // must append twice. Once for exact package and wildcard for child packages
          prefixes.append(",");
        }
        prefixes.append(Constants.BOOTSTRAP_PACKAGE_PREFIXES[i]).append(".*,");
        prefixes.append(Constants.BOOTSTRAP_PACKAGE_PREFIXES[i]);
      }
      PROPERTY_VALUE = prefixes.toString();
    }

    public static String getNewValue(final String existingValue) {
      if (null != existingValue
          && !"".equals(existingValue)
          && !existingValue.contains(PROPERTY_VALUE)) {
        return existingValue + "," + PROPERTY_VALUE;
      } else {
        return PROPERTY_VALUE;
      }
    }
  }
}
