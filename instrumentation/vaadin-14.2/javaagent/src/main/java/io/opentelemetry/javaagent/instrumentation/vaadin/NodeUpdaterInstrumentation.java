/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vaadin;

import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * This advice is used only when running tests. Works around an issue running testLatestDeps.
 * Currently the latest vaadin release is 20, it is likely that this advice can be deleted when
 * there is a new version.
 */
public class NodeUpdaterInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.vaadin.flow.server.frontend.NodeUpdater");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("getDefaultDevDependencies"),
        NodeUpdaterInstrumentation.class.getName() + "$FixDependenciesAdvice");
  }

  @SuppressWarnings("unused")
  public static class FixDependenciesAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Return Map<String, String> dependencies) {
      // If there is a dependency to workbox-webpack-plugin:6.1.0 add a dependency to
      // workbox-build:6.1.0. This is needed because workbox-build:6.2.0 that gets chosen without
      // having an explicit dependency to workbox-build here is incompatible with 6.1.0.
      if ("6.1.0".equals(dependencies.get("workbox-webpack-plugin"))) {
        dependencies.put("workbox-build", "6.1.0");
      }
    }
  }
}
