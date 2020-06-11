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
package io.opentelemetry.auto.bootstrap;

/**
 * Utility to track nested instrumentation.
 *
 * <p>For example, this can be used to track nested calls to super() in constructors by calling
 * #incrementCallDepth at the beginning of each constructor.
 */
public class CallDepthThreadLocalMap {

  private static final ClassValue<ThreadLocalDepth> TLS =
      new ClassValue<ThreadLocalDepth>() {
        @Override
        protected ThreadLocalDepth computeValue(Class<?> type) {
          return new ThreadLocalDepth();
        }
      };

  public static int incrementCallDepth(final Class<?> k) {
    return TLS.get(k).get().increment();
  }

  public static void reset(final Class<?> k) {
    TLS.get(k).get().depth = 0;
  }

  private static final class Depth {
    private int depth;

    private Depth() {
      this.depth = 0;
    }

    private int increment() {
      return this.depth++;
    }
  }

  private static final class ThreadLocalDepth extends ThreadLocal<Depth> {
    @Override
    protected Depth initialValue() {
      return new Depth();
    }
  }
}
