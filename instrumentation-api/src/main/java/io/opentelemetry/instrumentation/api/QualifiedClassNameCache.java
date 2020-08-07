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

package io.opentelemetry.instrumentation.api;

public class QualifiedClassNameCache {

  private static final int LEAF_SIZE = 16;

  private final Root root;

  public QualifiedClassNameCache(
      Function<Class<?>, String> formatter, TwoArgFunction<String, String, String> joiner) {
    this.root = new Root(formatter, joiner);
  }

  private static final class Root extends ClassValue<Leaf> {

    private final Function<Class<?>, String> formatter;
    private final TwoArgFunction<String, String, String> joiner;

    private Root(
        Function<Class<?>, String> formatter, TwoArgFunction<String, String, String> joiner) {
      this.formatter = formatter;
      this.joiner = joiner;
    }

    @Override
    protected Leaf computeValue(Class<?> type) {
      return new Leaf(formatter.apply(type), joiner);
    }
  }

  private static class Leaf {

    private final String name;

    private final FixedSizeCache<String, String> cache;
    private final Function<String, String> joiner;

    private Leaf(String name, TwoArgFunction<String, String, String> joiner) {
      this.name = name;
      this.cache = new FixedSizeCache<>(LEAF_SIZE);
      this.joiner = joiner.curry(name);
    }

    String get(String name) {
      return cache.computeIfAbsent(name, joiner);
    }

    String getName() {
      return name;
    }
  }

  public String getClassName(Class<?> klass) {
    return root.get(klass).getName();
  }

  public String getQualifiedName(Class<?> klass, String qualifier) {
    return root.get(klass).get(qualifier);
  }
}
