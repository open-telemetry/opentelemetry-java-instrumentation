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

package io.opentelemetry.javaagent.tooling.muzzle;

/**
 * Defines a set of packages for which we'll create references.
 *
 * <p>For now we're hardcoding this to the instrumentation and javaagent-tooling packages so we only
 * create references from the method advice and helper classes.
 */
final class ReferenceCreationPredicate {
  private static final String REFERENCE_CREATION_PACKAGE = "io.opentelemetry.instrumentation.";

  private static final String JAVA_AGENT_PACKAGE = "io.opentelemetry.javaagent.tooling.";

  private static final String[] REFERENCE_CREATION_PACKAGE_EXCLUDES = {
    "io.opentelemetry.instrumentation.api.", "io.opentelemetry.instrumentation.auto.api."
  };

  static boolean shouldCreateReferenceFor(String className) {
    if (!className.startsWith(REFERENCE_CREATION_PACKAGE)) {
      return className.startsWith(JAVA_AGENT_PACKAGE);
    }
    for (String exclude : REFERENCE_CREATION_PACKAGE_EXCLUDES) {
      if (className.startsWith(exclude)) {
        return false;
      }
    }
    return true;
  }

  private ReferenceCreationPredicate() {}
}
