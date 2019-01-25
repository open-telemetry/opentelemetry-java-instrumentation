package datadog.trace.instrumentation.jboss;

import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Constants;
import datadog.trace.agent.tooling.Instrumenter;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;

@AutoService(Instrumenter.class)
public final class JBossClassloadingInstrumentation extends Instrumenter.Default {
  public JBossClassloadingInstrumentation() {
    super("jboss-classloading");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.jboss.modules.Module");
  }

  @Override
  public void postMatch(
      final TypeDescription typeDescription,
      final ClassLoader classLoader,
      final JavaModule module,
      final Class<?> classBeingRedefined,
      final ProtectionDomain protectionDomain) {
    // Set the system prop to tell jboss to delegate classloads for datadog bootstrap classes
    final StringBuilder prefixes = new StringBuilder("");
    for (int i = 0; i < Constants.BOOTSTRAP_PACKAGE_PREFIXES.length; ++i) {
      if (i > 0) {
        prefixes.append(",");
      }
      prefixes.append(Constants.BOOTSTRAP_PACKAGE_PREFIXES[i]);
    }
    final String existing = System.getProperty("jboss.modules.system.pkgs");
    if (null == existing) {
      System.setProperty("jboss.modules.system.pkgs", prefixes.toString());
    } else if (!existing.contains(prefixes)) {
      System.setProperty("jboss.modules.system.pkgs", existing + "," + prefixes.toString());
    }
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return Collections.emptyMap();
  }
}
