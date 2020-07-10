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

package io.opentelemetry.auto.instrumentation.reactor;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isTypeInitializer;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class ReactorHooksInstrumentation extends Instrumenter.Default {

  public ReactorHooksInstrumentation() {
    super("reactor-core");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("reactor.core.publisher.Hooks");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".shaded.TracingPublishers",
      packageName + ".shaded.TracingPublishers$MonoTracingPublisher",
      packageName + ".shaded.TracingPublishers$ParallelFluxTracingPublisher",
      packageName + ".shaded.TracingPublishers$ConnectableFluxTracingPublisher",
      packageName + ".shaded.TracingPublishers$GroupedFluxTracingPublisher",
      packageName + ".shaded.TracingPublishers$FluxTracingPublisher",
      packageName + ".shaded.TracingPublishers$FuseableMonoTracingPublisher",
      packageName + ".shaded.TracingPublishers$FuseableParallelFluxTracingPublisher",
      packageName + ".shaded.TracingPublishers$FuseableConnectableFluxTracingPublisher",
      packageName + ".shaded.TracingPublishers$FuseableGroupedFluxTracingPublisher",
      packageName + ".shaded.TracingPublishers$FuseableFluxTracingPublisher",
      packageName + ".shaded.TracingSubscriber"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isTypeInitializer().or(named("resetOnEachOperator")), packageName + ".ReactorHooksAdvice");
  }
}
