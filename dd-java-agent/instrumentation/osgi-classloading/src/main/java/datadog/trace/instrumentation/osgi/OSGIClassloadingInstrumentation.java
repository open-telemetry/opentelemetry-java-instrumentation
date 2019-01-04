package datadog.trace.instrumentation.osgi;

import static java.util.Collections.emptyMap;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.Utils;
import java.security.ProtectionDomain;
import java.util.Map;
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
    // OSGI Bundle class loads the sys prop which defines bootstrap classes
    return named("org.osgi.framework.Bundle");
  }

  @Override
  public void postMatch(
      final TypeDescription typeDescription,
      final ClassLoader classLoader,
      final JavaModule module,
      final Class<?> classBeingRedefined,
      final ProtectionDomain protectionDomain) {
    // Set the system prop to tell osgi to delegate classloads for datadog bootstrap classes
    final StringBuilder ddPrefixes = new StringBuilder("");
    for (int i = 0; i < Utils.BOOTSTRAP_PACKAGE_PREFIXES.length; ++i) {
      if (i > 0) {
        // must append twice. Once for exact package and wildcard for child packages
        ddPrefixes.append(",");
      }
      ddPrefixes.append(Utils.BOOTSTRAP_PACKAGE_PREFIXES[i]).append(".*,");
      ddPrefixes.append(Utils.BOOTSTRAP_PACKAGE_PREFIXES[i]);
    }
    final String existing = System.getProperty("org.osgi.framework.bootdelegation");
    if (null == existing) {
      System.setProperty("org.osgi.framework.bootdelegation", ddPrefixes.toString());
    } else if (!existing.contains(ddPrefixes)) {
      System.setProperty(
          "org.osgi.framework.bootdelegation", existing + "," + ddPrefixes.toString());
    }
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return emptyMap();
  }
}
