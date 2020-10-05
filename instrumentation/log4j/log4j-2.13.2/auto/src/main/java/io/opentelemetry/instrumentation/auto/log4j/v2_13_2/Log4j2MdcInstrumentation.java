/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.log4j.v2_13_2;

import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class Log4j2MdcInstrumentation extends Instrumenter.Default {
  public Log4j2MdcInstrumentation() {
    super("log4j2", "log4j", "log4j-2.13.2");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("org.apache.logging.log4j.core.util.ContextDataProvider");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    // we cannot use ContextDataProvider here because one of the classes that we inject implements
    // this interface, causing the interface to be loaded while it's being transformed, which leads
    // to duplicate class definition error after the interface is transformed and the triggering
    // class loader tries to load it.
    return named("org.apache.logging.log4j.core.impl.ThreadContextDataInjector");
  }

  @Override
  public String[] helperResourceNames() {
    return new String[] {
      "META-INF/services/org.apache.logging.log4j.core.util.ContextDataProvider",
    };
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.instrumentation.log4j.v2_13_2.OpenTelemetryContextDataProvider"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    // Nothing to instrument, injecting helper resource & class is enough
    return Collections.emptyMap();
  }
}
