package datadog.trace.instrumentation.jboss;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.Utils;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.NameMatcher;
import net.bytebuddy.matcher.StringMatcher;

@AutoService(Instrumenter.class)
public final class JBossClassloadingInstrumentation extends Instrumenter.Default {
  public JBossClassloadingInstrumentation() {
    super("jboss-classloading");
  }

  @Override
  public ElementMatcher typeMatcher() {
    return new NameMatcher(
        new StringMatcher("org.jboss.modules.Module", StringMatcher.Mode.EQUALS_FULLY) {
          @Override
          public boolean matches(String target) {
            if (super.matches(target)) {
              // This instrumentation modifies no bytes.
              // Instead it sets a system prop to tell jboss to delegate
              // classloads for datadog bootstrap classes
              StringBuilder ddPrefixes = new StringBuilder("");
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
                System.setProperty(
                    "jboss.modules.system.pkgs", existing + "," + ddPrefixes.toString());
              }
            }
            return false;
          }
        });
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    return Collections.emptyMap();
  }
}
