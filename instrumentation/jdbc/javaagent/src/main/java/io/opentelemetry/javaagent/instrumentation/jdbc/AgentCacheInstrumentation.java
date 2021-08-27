/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import static net.bytebuddy.matcher.ElementMatchers.none;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class AgentCacheInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return none();
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        none(), AgentCacheInstrumentation.class.getName() + "$InitAdvice");
  }

  @SuppressWarnings("unused")
  public static class InitAdvice {
    public void init() {
      // the sole purpose of this advice is to ensure that AgentDataStoreFactory is recognized
      // as helper class and injected into class loader
      AgentCacheFactory.class.getName();
    }
  }
}
