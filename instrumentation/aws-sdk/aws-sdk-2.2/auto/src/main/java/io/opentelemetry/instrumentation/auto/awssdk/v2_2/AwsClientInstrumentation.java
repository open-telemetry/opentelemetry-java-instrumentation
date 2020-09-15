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

package io.opentelemetry.instrumentation.auto.awssdk.v2_2;

import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Injects resource file with reference to our {@link TracingExecutionInterceptor} to allow SDK's
 * service loading mechanism to pick it up.
 */
@AutoService(Instrumenter.class)
public class AwsClientInstrumentation extends AbstractAwsClientInstrumentation {
  @Override
  public String[] helperResourceNames() {
    return new String[] {
      "software/amazon/awssdk/global/handlers/execution.interceptors",
    };
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // We don't actually transform it but want to make sure we only apply the instrumentation when
    // our key dependency is present.
    return hasClassesNamed("software.amazon.awssdk.core.interceptor.ExecutionInterceptor");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    // We don't actually need to transform anything but need a class to match to make sure our
    // helpers are injected. Pick an arbitrary class we happen to reference.
    return named("software.amazon.awssdk.core.SdkRequest");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    // Nothing to do, helpers are injected but no class transformation happens here.
    return Collections.emptyMap();
  }
}
