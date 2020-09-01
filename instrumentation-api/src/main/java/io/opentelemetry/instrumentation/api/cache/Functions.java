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

package io.opentelemetry.instrumentation.api.cache;

public final class Functions {

  // TODO the majority of this can be removed/simplified when dropping Java 7

  public static final class Zero<T> implements Function<T, T> {

    @Override
    public T apply(T input) {
      return input;
    }
  }

  @SuppressWarnings("rawtypes")
  private static final Zero ZERO = new Zero();

  @SuppressWarnings("unchecked")
  public static <T> Zero<T> zero() {
    return (Zero<T>) ZERO;
  }

  public abstract static class Concatenate implements TwoArgFunction<String, String, String> {

    @Override
    public String apply(String left, String right) {
      return left + right;
    }
  }

  public static final class Suffix extends Concatenate implements Function<String, String> {
    private final String suffix;
    private final Function<String, String> transformer;

    public Suffix(String suffix, Function<String, String> transformer) {
      this.suffix = suffix;
      this.transformer = transformer;
    }

    public Suffix(String suffix) {
      this(suffix, Functions.<String>zero());
    }

    @Override
    public String apply(String key) {
      return apply(transformer.apply(key), suffix);
    }

    @Override
    public Function<String, String> curry(String suffix) {
      return new Suffix(suffix, transformer);
    }

    public static final Suffix ZERO = new Suffix("", Functions.<String>zero());
  }

  public static final class Prefix extends Concatenate implements Function<String, String> {
    private final String prefix;
    private final Function<String, String> transformer;

    public Prefix(String prefix, Function<String, String> transformer) {
      this.prefix = prefix;
      this.transformer = transformer;
    }

    public Prefix(String prefix) {
      this(prefix, Functions.<String>zero());
    }

    @Override
    public String apply(String key) {
      return apply(prefix, transformer.apply(key));
    }

    @Override
    public Function<String, String> curry(String prefix) {
      return new Prefix(prefix, transformer);
    }

    public static final Prefix ZERO = new Prefix("", Functions.<String>zero());
  }

  public abstract static class Join implements TwoArgFunction<String, String, String> {
    protected final String joiner;
    protected final Function<String, String> transformer;

    protected Join(String joiner, Function<String, String> transformer) {
      this.joiner = joiner;
      this.transformer = transformer;
    }

    @Override
    public String apply(String left, String right) {
      return left + joiner + right;
    }
  }

  public static class PrefixJoin extends Join {

    public PrefixJoin(String joiner, Function<String, String> transformer) {
      super(joiner, transformer);
    }

    @Override
    public Function<String, String> curry(String specialisation) {
      return new Prefix(specialisation + joiner, transformer);
    }

    public static PrefixJoin of(String joiner, Function<String, String> transformer) {
      return new PrefixJoin(joiner, transformer);
    }

    public static PrefixJoin of(String joiner) {
      return of(joiner, Functions.<String>zero());
    }
  }

  public static class SuffixJoin extends Join {

    public SuffixJoin(String joiner, Function<String, String> transformer) {
      super(joiner, transformer);
    }

    @Override
    public Function<String, String> curry(String specialisation) {
      return new Suffix(joiner + specialisation, transformer);
    }

    public static SuffixJoin of(String joiner, Function<String, String> transformer) {
      return new SuffixJoin(joiner, transformer);
    }

    public static SuffixJoin of(String joiner) {
      return of(joiner, Functions.<String>zero());
    }
  }

  public static final class LowerCase implements Function<String, String> {

    @Override
    public String apply(String key) {
      return key.toLowerCase();
    }
  }

  public static final class ToString<T> implements Function<T, String> {

    @Override
    public String apply(T key) {
      return key.toString();
    }
  }
}
