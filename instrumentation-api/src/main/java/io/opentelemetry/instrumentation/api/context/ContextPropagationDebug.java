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

package io.opentelemetry.instrumentation.api.context;

import io.grpc.Context;
import java.util.List;

public final class ContextPropagationDebug {

  // locations where the context was propagated to another thread (tracking multiple steps is
  // helpful in akka where there is so much recursive async spawning of new work)
  private static final Context.Key<List<StackTraceElement[]>> THREAD_PROPAGATION_LOCATIONS =
      Context.key("thread-propagation-locations");
  private static final boolean THREAD_PROPAGATION_DEBUGGER =
      Boolean.getBoolean("otel.threadPropagationDebugger");

  public static boolean isThreadPropagationDebuggerEnabled() {
    return THREAD_PROPAGATION_DEBUGGER;
  }

  public static List<StackTraceElement[]> getLocations(Context context) {
    return THREAD_PROPAGATION_LOCATIONS.get(context);
  }

  public static Context withLocations(List<StackTraceElement[]> locations, Context context) {
    return context.withValue(THREAD_PROPAGATION_LOCATIONS, locations);
  }

  private ContextPropagationDebug() {}
}
