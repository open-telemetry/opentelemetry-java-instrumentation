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
package io.opentelemetry.auto.bootstrap.instrumentation.java.concurrent;

import lombok.extern.slf4j.Slf4j;

/**
 * This is used to wrap lambda runnables since currently we cannot instrument them
 *
 * <p>FIXME: We should remove this once https://github.com/raphw/byte-buddy/issues/558 is fixed
 */
@Slf4j
public final class RunnableWrapper implements Runnable {
  private final Runnable runnable;

  public RunnableWrapper(final Runnable runnable) {
    this.runnable = runnable;
  }

  @Override
  public void run() {
    runnable.run();
  }

  public static Runnable wrapIfNeeded(final Runnable task) {
    // We wrap only lambdas' anonymous classes and if given object has not already been wrapped.
    // Anonymous classes have '/' in class name which is not allowed in 'normal' classes.
    if (task.getClass().getName().contains("/") && (!(task instanceof RunnableWrapper))) {
      log.debug("Wrapping runnable task {}", task);
      return new RunnableWrapper(task);
    }
    return task;
  }
}
