/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.common.service;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ServletOutputStreamInstrumentation implements TypeInstrumentation {
  private final String basePackageName;
  private final String writeBytesAndOffsetClassName;
  private final String writeBytesClassName;
  private final String writeIntAdviceClassName;

  public ServletOutputStreamInstrumentation(
      String basePackageName,
      String writeBytesAndOffsetClassName,
      String writeBytesClassName,
      String writeIntAdviceClassName) {
    this.basePackageName = basePackageName;
    this.writeBytesAndOffsetClassName = writeBytesAndOffsetClassName;
    this.writeBytesClassName = writeBytesClassName;
    this.writeIntAdviceClassName = writeIntAdviceClassName;
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed(basePackageName + ".ServletOutputStream");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperType(namedOneOf(basePackageName + ".ServletOutputStream"));
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
        writeBytesAndOffsetClassName);
    transformer.applyAdviceToMethod(
        named("write").and(takesArguments(1)).and(takesArgument(0, byte[].class)).and(isPublic()),
        writeBytesClassName);
    transformer.applyAdviceToMethod(
        named("write").and(takesArguments(1)).and(takesArgument(0, int.class)).and(isPublic()),
        writeIntAdviceClassName);
  }
}
