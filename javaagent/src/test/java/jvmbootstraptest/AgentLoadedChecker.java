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

package jvmbootstraptest;

import java.net.URL;
import java.net.URLClassLoader;

public class AgentLoadedChecker {
  public static void main(final String[] args) throws ClassNotFoundException {
    // Empty classloader that delegates to bootstrap
    URLClassLoader emptyClassLoader = new URLClassLoader(new URL[] {}, null);
    Class agentClass = emptyClassLoader.loadClass("io.opentelemetry.auto.bootstrap.Agent");

    if (agentClass.getClassLoader() != null) {
      throw new RuntimeException(
          "Agent loaded into classloader other than bootstrap: " + agentClass.getClassLoader());
    }
  }
}
