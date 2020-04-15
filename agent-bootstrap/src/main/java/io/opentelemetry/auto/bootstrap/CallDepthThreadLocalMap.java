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

import java.util.HashMap;
import java.util.Map;

/**
 * Utility to track nested instrumentation.
 *
 * <p>For example, this can be used to track nested calls to super() in constructors by calling
 * #incrementCallDepth at the beginning of each constructor.
 */
public class CallDepthThreadLocalMap {
  private static final ThreadLocal<Map<Object, Integer>> TLS =
      new ThreadLocal<Map<Object, Integer>>() {
        @Override
        public Map<Object, Integer> initialValue() {
          return new HashMap<>();
        }
      };

  public static int incrementCallDepth(final Object k) {
    final Map<Object, Integer> map = TLS.get();
    Integer depth = map.get(k);
    if (depth == null) {
      depth = 0;
    } else {
      depth += 1;
    }
    map.put(k, depth);
    return depth;
  }

  public static void reset(final Object k) {
    TLS.get().remove(k);
  }
}
