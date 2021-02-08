/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachedubbo.v2_7;

import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class DubboInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("org.apache.dubbo.common.extension.ExtensionLoader");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return Collections.emptyMap();
  }
}
