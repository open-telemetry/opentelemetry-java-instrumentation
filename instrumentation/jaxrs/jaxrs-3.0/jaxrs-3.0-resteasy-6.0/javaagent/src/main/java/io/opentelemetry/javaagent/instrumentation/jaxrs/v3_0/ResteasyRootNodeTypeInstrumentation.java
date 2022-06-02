/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v3_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jaxrs.JaxrsPathUtil;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import org.jboss.resteasy.core.ResourceLocatorInvoker;
import org.jboss.resteasy.core.ResourceMethodInvoker;

public class ResteasyRootNodeTypeInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.jboss.resteasy.core.registry.RootNode");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("addInvoker")
            .and(takesArgument(0, String.class))
            // package of ResourceInvoker was changed in reasteasy 4
            .and(
                takesArgument(
                    1,
                    namedOneOf(
                        "org.jboss.resteasy.core.ResourceInvoker",
                        "org.jboss.resteasy.spi.ResourceInvoker"))),
        ResteasyRootNodeTypeInstrumentation.class.getName() + "$AddInvokerAdvice");
  }

  @SuppressWarnings("unused")
  public static class AddInvokerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void addInvoker(
        @Advice.Argument(0) String path,
        @Advice.Argument(value = 1, typing = Assigner.Typing.DYNAMIC) Object invoker) {
      String normalizedPath = JaxrsPathUtil.normalizePath(path);
      if (invoker instanceof ResourceLocatorInvoker) {
        ResourceLocatorInvoker resourceLocatorInvoker = (ResourceLocatorInvoker) invoker;
        VirtualField.find(ResourceLocatorInvoker.class, String.class)
            .set(resourceLocatorInvoker, normalizedPath);
      } else if (invoker instanceof ResourceMethodInvoker) {
        ResourceMethodInvoker resourceMethodInvoker = (ResourceMethodInvoker) invoker;
        VirtualField.find(ResourceMethodInvoker.class, String.class)
            .set(resourceMethodInvoker, normalizedPath);
      }
    }
  }
}
