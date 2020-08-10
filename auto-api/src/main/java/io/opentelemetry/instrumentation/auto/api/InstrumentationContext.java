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

package io.opentelemetry.instrumentation.auto.api;

/** Instrumentation Context API */
public class InstrumentationContext {
  private InstrumentationContext() {}

  /**
   * Find a {@link ContextStore} instance for given key class and context class.
   *
   * <p>Conceptually this can be thought of as a map lookup to fetch a second level map given
   * keyClass.
   *
   * <p>In reality, the <em>calls</em> to this method are re-written to something more performant
   * while injecting advice into a method.
   *
   * <p>This method must only be called within an Advice class.
   *
   * @param keyClass The key class context is attached to.
   * @param contextClass The context class attached to the user class.
   * @param <K> key class
   * @param <C> context class
   * @return The instance of context store for given arguments.
   */
  public static <K, C> ContextStore<K, C> get(
      final Class<K> keyClass, final Class<C> contextClass) {
    throw new RuntimeException(
        "Calls to this method will be rewritten by Instrumentation Context Provider (e.g. FieldBackedProvider)");
  }
}
