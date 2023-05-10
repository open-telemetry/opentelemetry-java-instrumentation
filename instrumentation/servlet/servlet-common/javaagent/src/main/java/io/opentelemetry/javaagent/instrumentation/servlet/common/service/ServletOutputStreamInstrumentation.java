/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.common.service;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ServletOutputStreamInstrumentation implements TypeInstrumentation {
  private final String basePackageName;
  private final String writeBytesAndOffsetAdviceClassName;
  private final String writeBytesAdviceClassName;
  private final String writeIntAdviceClassName;

  public ServletOutputStreamInstrumentation(
      String basePackageName,
      String writeBytesAndOffsetAdviceClassName,
      String writeBytesAdviceClassName,
      String writeIntAdviceClassName) {
    this.basePackageName = basePackageName;
    this.writeBytesAndOffsetAdviceClassName = writeBytesAndOffsetAdviceClassName;
    this.writeBytesAdviceClassName = writeBytesAdviceClassName;
    this.writeIntAdviceClassName = writeIntAdviceClassName;
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed(basePackageName + ".ServletOutputStream");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperType(named(basePackageName + ".ServletOutputStream"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("write")
            .and(takesArguments(3))
            .and(takesArgument(0, byte[].class))
            .and(takesArgument(1, int.class))
            .and(takesArgument(2, int.class))
            .and(isPublic()),
        writeBytesAndOffsetAdviceClassName);
    transformer.applyAdviceToMethod(
        named("write").and(takesArguments(1)).and(takesArgument(0, byte[].class)).and(isPublic()),
        writeBytesAdviceClassName);
    transformer.applyAdviceToMethod(
        named("write").and(takesArguments(1)).and(takesArgument(0, int.class)).and(isPublic()),
        writeIntAdviceClassName);
  }
}
