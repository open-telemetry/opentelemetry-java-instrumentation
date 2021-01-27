/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.dubbo.apache.v2_7;

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.instrumentation.dubbo.apache.v2_7.TracingFilter;
import io.opentelemetry.javaagent.instrumentation.dubbo.apache.v2_7.filter.SimpleFilter;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.dubbo.rpc.Filter;

public class DubboFilterInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.dubbo.common.extension.ExtensionLoader");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("org.apache.dubbo.common.extension.ExtensionLoader");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        named("getExtension"), DubboFilterInstrumentation.class.getName() + "$RemoveActiveFilter");
    transformers.put(
        named("getActivateExtension")
            .and(takesArguments(3))
            .and(takesArgument(0, named("org.apache.dubbo.common.URL")))
            .and(takesArgument(1, String.class))
            .and(takesArgument(2, String.class))
            .and(isPublic()),
        DubboFilterInstrumentation.class.getName() + "$AddFilterAdvice");
    return transformers;
  }

  public static class RemoveActiveFilter {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void removeFilter(
        @Advice.Argument(0) String name, @Advice.Return(readOnly = false) Object object) {
      if (name.equals("OtelTracingFilter")) {
        object = new SimpleFilter();
      }
    }
  }

  public static class AddFilterAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void addFilter(
        @Advice.Argument(2) String group, @Advice.Return(readOnly = false) List<?> filters) {
      if (!filters.isEmpty() && filters.get(0) instanceof Filter) {
        List<Filter> filterList = (List<Filter>) filters;
        if (group.equals("provider") || group.equals("consumer")) {
          filterList.add(filterList.size(), new TracingFilter());
        }
        filters = filterList;
      }
    }
  }
}
