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

package io.opentelemetry.auto.bootstrap.instrumentation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;

public class SafeServiceLoader {

  private static final Logger log = LoggerFactory.getLogger(SafeServiceLoader.class);

  /**
   * Delegates to {@link ServiceLoader#load(Class, ClassLoader)} and then eagerly iterates over
   * returned {@code Iterable}, ignoring any potential {@link UnsupportedClassVersionError}.
   *
   * <p>Those errors can happen when some classes returned by {@code ServiceLoader} were compiled
   * for later java version than is used by currently running JVM. During normal course of business
   * this should not happen. Please read CONTRIBUTING.md, section "Testing - Java versions" for a
   * background info why this is Ok.
   */
  public static <T> Iterable<T> load(Class<T> serviceClass, ClassLoader classLoader) {
    List<T> result = new ArrayList<>();
    java.util.ServiceLoader<T> services = ServiceLoader.load(serviceClass, classLoader);
    for (Iterator<T> iter = services.iterator(); iter.hasNext(); ) {
      try {
        result.add(iter.next());
      } catch (UnsupportedClassVersionError e) {
        log.debug("Unable to load instrumentation class: {}", e.getMessage());
      }
    }
    return result;
  }
}
