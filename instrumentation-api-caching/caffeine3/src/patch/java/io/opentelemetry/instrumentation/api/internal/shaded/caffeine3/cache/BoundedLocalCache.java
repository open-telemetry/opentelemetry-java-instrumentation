/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Copyright 2017 Datadog, Inc.
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
/*
 * Copyright 2014 Ben Manes. All Rights Reserved.
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

package io.opentelemetry.instrumentation.api.internal.shaded.caffeine3.cache;

import java.lang.ref.WeakReference;

/** skeleton outer class just for compilation purposes, not included in the final patch. */
abstract class BoundedLocalCache<K, V> {
  abstract void performCleanUp(Runnable task);

  /** patched to not extend ForkJoinTask as we don't want that class loaded too early. */
  static final class PerformCleanupTask implements Runnable {
    private static final long serialVersionUID = 1L;

    final WeakReference<BoundedLocalCache<?, ?>> reference;

    PerformCleanupTask(BoundedLocalCache<?, ?> cache) {
      reference = new WeakReference<>(cache);
    }

    @Override
    public void run() {
      BoundedLocalCache<?, ?> cache = reference.get();
      if (cache != null) {
        cache.performCleanUp(/* ignored */ null);
      }
    }
  }
}
