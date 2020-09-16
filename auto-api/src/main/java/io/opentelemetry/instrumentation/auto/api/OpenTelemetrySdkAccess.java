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

import java.util.concurrent.TimeUnit;

/**
 * A helper to facilitate accessing OpenTelemetry SDK methods from instrumentation. Because
 * instrumentation runs in the app classloader, they do not have access to our SDK in the agent
 * classloader. So we use this class in the bootstrap classloader to bridge between the two - the
 * agent classloader will register implementations of needed SDK functions that can be called from
 * instrumentation.
 */
public class OpenTelemetrySdkAccess {

  /**
   * Interface matching {@link io.opentelemetry.sdk.trace.TracerSdkProvider#forceFlush()} to allow
   * holding a reference to it.
   */
  public interface ForceFlusher {
    /** Executes force flush. */
    void run(int timeout, TimeUnit unit);
  }

  private static volatile ForceFlusher FORCE_FLUSH;

  /** Forces flush of pending spans and metrics. */
  public static void forceFlush(int timeout, TimeUnit unit) {
    FORCE_FLUSH.run(timeout, unit);
  }

  /**
   * Sets the {@link Runnable} to execute when instrumentation needs to force flush. This is called
   * from the agent classloader to execute the SDK's force flush mechanism. Instrumentation must not
   * call this.
   */
  public static void internalSetForceFlush(ForceFlusher forceFlush) {
    if (FORCE_FLUSH != null) {
      // Only possible by misuse of this API, just ignore.
      return;
    }
    FORCE_FLUSH = forceFlush;
  }
}
