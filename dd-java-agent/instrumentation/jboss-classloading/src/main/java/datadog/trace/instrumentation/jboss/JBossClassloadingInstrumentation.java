package datadog.trace.instrumentation.jboss;

import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.Utils;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.Map;
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
      TypeDescription typeDescription,
      ClassLoader classLoader,
      JavaModule module,
      Class<?> classBeingRedefined,
      ProtectionDomain protectionDomain) {
    // Set the system prop to tell jboss to delegate classloads for datadog bootstrap classes
    final StringBuilder ddPrefixes = new StringBuilder("");
    for (int i = 0; i < Utils.BOOTSTRAP_PACKAGE_PREFIXES.length; ++i) {
      if (i > 0) {
        ddPrefixes.append(",");
      }
      ddPrefixes.append(Utils.BOOTSTRAP_PACKAGE_PREFIXES[i]);
    }
    final String existing = System.getProperty("jboss.modules.system.pkgs");
    if (null == existing) {
      System.setProperty("jboss.modules.system.pkgs", ddPrefixes.toString());
    } else if (!existing.contains(ddPrefixes)) {
      System.setProperty("jboss.modules.system.pkgs", existing + "," + ddPrefixes.toString());
    }
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    return Collections.emptyMap();
  }
}
