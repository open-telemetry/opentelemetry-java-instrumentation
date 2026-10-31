/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.matcher;

import java.util.Iterator;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class NamesMatcher
    extends ElementMatcher.Junction.AbstractBase<Iterable<? extends TypeDescription>> {

  private final String[] names;

  NamesMatcher(String... names) {
    this.names = names.clone();
  }

  @Override
  public boolean matches(Iterable<? extends TypeDescription> target) {
    Iterator<? extends TypeDescription> iterator = target.iterator();
    for (String name : names) {
      if (!iterator.hasNext() || !name.equals(iterator.next().getName())) {
        return false;
      }
    }
    return !iterator.hasNext();
  }
}
