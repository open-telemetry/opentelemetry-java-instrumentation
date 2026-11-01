/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.matcher;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterList;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.matcher.ElementMatcher;

class TakesArgumentsMatcher<T extends MethodDescription>
    extends ElementMatcher.Junction.AbstractBase<T> {

  private final String[] names;

  TakesArgumentsMatcher(String... names) {
    this.names = names.clone();
  }

  @Override
  public boolean matches(T target) {
    ParameterList<?> parameters = target.getParameters();
    if (parameters.size() != names.length) {
      return false;
    }

    TypeList.Generic parameterTypes = parameters.asTypeList();
    for (int index = 0; index < names.length; index++) {
      if (!names[index].equals(parameterTypes.get(index).asErasure().getName())) {
        return false;
      }
    }
    return true;
  }
}
