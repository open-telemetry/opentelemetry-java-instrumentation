/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.matcher;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.matcher.ElementMatcher;

class TakesArgumentsMatcher<T extends MethodDescription>
    extends ElementMatcher.Junction.AbstractBase<T> {

  private final ElementMatcher<? super TypeDescription>[] matchers;

  TakesArgumentsMatcher(ElementMatcher<? super TypeDescription>[] matchers) {
    this.matchers = matchers.clone();
  }

  @Override
  public boolean matches(T target) {
    ParameterList<?> parameters = target.getParameters();
    if (parameters.size() != matchers.length) {
      return false;
    }

    TypeList.Generic parameterTypes = parameters.asTypeList();
    for (int index = 0; index < matchers.length; index++) {
      if (!matchers[index].matches(parameterTypes.get(index).asErasure())) {
        return false;
      }
    }
    return true;
  }
}
