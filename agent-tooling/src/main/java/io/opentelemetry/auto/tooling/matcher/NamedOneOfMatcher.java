/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.tooling.matcher;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.matcher.ElementMatcher;

public class NamedOneOfMatcher {

  /**
   * Matches a {@link NamedElement} for its exact name's membership of a set.
   *
   * @param names The expected names.
   * @param <T> The type of the matched object.
   * @return An element matcher checking if an element's exact name is a member of a set.
   */
  public static <T extends NamedElement> ElementMatcher.Junction<T> namedOneOf(String... names) {
    return new SetMatcher<>(names);
  }

  private static class SetMatcher<T extends NamedElement>
      extends ElementMatcher.Junction.AbstractBase<T> {

    // TODO better to use equality/prefix based set,
    // they take up less space and are membership checks quicker
    private final Set<String> values;

    private SetMatcher(String... values) {
      this.values = new HashSet<>(Arrays.asList(values));
    }

    @Override
    public boolean matches(T target) {
      return values.contains(target.getActualName());
    }
  }
}
