/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v2_2;

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
