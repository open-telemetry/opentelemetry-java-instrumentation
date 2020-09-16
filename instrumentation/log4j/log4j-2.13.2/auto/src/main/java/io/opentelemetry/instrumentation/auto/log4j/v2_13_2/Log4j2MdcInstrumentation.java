/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    // need to match a class, not an interface (ContextDataProvider) - otherwise helper classes
    // and resources are not injected
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
