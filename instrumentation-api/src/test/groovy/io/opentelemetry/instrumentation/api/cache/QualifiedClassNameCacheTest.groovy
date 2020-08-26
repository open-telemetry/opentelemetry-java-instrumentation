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

package io.opentelemetry.instrumentation.api

import io.opentelemetry.instrumentation.api.cache.Function
import io.opentelemetry.instrumentation.api.cache.Functions
import io.opentelemetry.instrumentation.api.cache.QualifiedClassNameCache
import spock.lang.Specification

class QualifiedClassNameCacheTest extends Specification {

  def "test cached string operations"() {
    when:
    QualifiedClassNameCache cache = new QualifiedClassNameCache(new Function<Class<?>, String>() {
      @Override
      String apply(Class<?> input) {
        return input.getSimpleName()
      }
    }, func)
    String qualified = cache.getQualifiedName(type, prefix)

    then:
    qualified == expected
    where:
    type   | prefix | func                                                | expected
    String | "foo." | Functions.Suffix.ZERO                               | "foo.String"
    String | ".foo" | Functions.Prefix.ZERO                               | "String.foo"
    String | "foo"  | Functions.SuffixJoin.of(".")                        | "foo.String"
    String | "foo"  | Functions.PrefixJoin.of(".")                        | "String.foo"
    String | "foo"  | Functions.SuffixJoin.of(".", new Replace("oo", "")) | "f.String"
    String | "foo"  | Functions.PrefixJoin.of(".", new Replace("oo", "")) | "String.f"
  }

  def "test get cached class name"() {
    when:
    QualifiedClassNameCache cache = new QualifiedClassNameCache(new Function<Class<?>, String>() {
      @Override
      String apply(Class<?> input) {
        return input.getSimpleName()
      }
    }, Functions.Prefix.ZERO)
    then:
    cache.getClassName(type) == expected

    where:
    type           | expected
    String         | "String"
    Functions.Zero | "Zero"
  }

  class Replace implements Function<String, String> {
    private final String find
    private final String replace

    Replace(String find, String replace) {
      this.find = find
      this.replace = replace
    }

    @Override
    String apply(String input) {
      return input.replace(find, replace)
    }
  }
}
