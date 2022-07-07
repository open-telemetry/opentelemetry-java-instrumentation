/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy;

import static net.bytebuddy.matcher.ElementMatchers.failSafe;
import static net.bytebuddy.matcher.ElementMatchers.hasParameters;
import static net.bytebuddy.matcher.ElementMatchers.hasType;
import static net.bytebuddy.matcher.ElementMatchers.isVisibleTo;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.whereNone;

import java.security.ProtectionDomain;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.scaffold.inline.MethodNameTransformer;
import net.bytebuddy.utility.JavaModule;

/**
 * Wrapper for {@link AgentBuilder.TypeStrategy} that excludes methods with missing return or
 * parameter types. By default, byte-buddy fails transforming such classes.
 */
public final class SafeTypeStrategy implements AgentBuilder.TypeStrategy {
  private final AgentBuilder.TypeStrategy delegate;

  public SafeTypeStrategy(AgentBuilder.TypeStrategy delegate) {
    this.delegate = delegate;
  }

  @Override
  public DynamicType.Builder<?> builder(
      TypeDescription typeDescription,
      ByteBuddy byteBuddy,
      ClassFileLocator classFileLocator,
      MethodNameTransformer methodNameTransformer,
      ClassLoader classLoader,
      JavaModule module,
      ProtectionDomain protectionDomain) {
    // type description wrapper that removes methods with missing return or parameter types
    TypeDescription newTypeDescription =
        new TypeDescription.AbstractBase.OfSimpleType.WithDelegation() {

          @Override
          public String getName() {
            return delegate().getName();
          }

          @Override
          protected TypeDescription delegate() {
            return typeDescription;
          }

          @Override
          public MethodList<MethodDescription.InDefinedShape> getDeclaredMethods() {
            MethodList<MethodDescription.InDefinedShape> methodList = super.getDeclaredMethods();
            return filterMethods(methodList, typeDescription);
          }
        };

    return delegate.builder(
        newTypeDescription,
        byteBuddy,
        classFileLocator,
        methodNameTransformer,
        classLoader,
        module,
        protectionDomain);
  }

  private static <T extends MethodDescription> MethodList<T> filterMethods(
      MethodList<T> methodList, TypeDescription viewPoint) {
    // filter out methods missing return or parameter types
    return methodList.filter(
        failSafe(
            returns(isVisibleTo(viewPoint))
                .and(hasParameters(whereNone(hasType(not(isVisibleTo(viewPoint))))))));
  }
}
