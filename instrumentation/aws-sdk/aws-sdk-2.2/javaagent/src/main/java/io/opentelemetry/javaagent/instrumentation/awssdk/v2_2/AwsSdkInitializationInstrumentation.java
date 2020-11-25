/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v2_2;

import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

final class AwsSdkInitializationInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    // This is essentially the entry point of the AWS SDK, all clients implement it. We can ensure
    // our interceptor service definition is injected as early as possible if we typematch against
    // it.
    return named("software.amazon.awssdk.core.SdkClient");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    // Don't need to transform, just want to make sure helpers are injected as early as possible.
    return Collections.emptyMap();
  }
}
