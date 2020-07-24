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

package io.opentelemetry.auto.test

import io.opentelemetry.auto.bootstrap.AgentClassLoader
import io.opentelemetry.auto.tooling.ClassLoaderMatcher
import io.opentelemetry.auto.tooling.ExporterClassLoader
import io.opentelemetry.auto.tooling.log.LogContextScopeListener
import io.opentelemetry.auto.util.test.AgentSpecification

class ClassLoaderMatcherTest extends AgentSpecification {

  def "skips agent classloader"() {
    setup:
    URL root = new URL("file://")
    URLClassLoader agentLoader = new AgentClassLoader(root, null, null)
    expect:
    ClassLoaderMatcher.skipClassLoader().matches(agentLoader)
  }

  def "skips exporter classloader"() {
    setup:
    URL url = new URL("file://")
    URLClassLoader exporterLoader = new ExporterClassLoader(url, null)
    expect:
    ClassLoaderMatcher.skipClassLoader().matches(exporterLoader)
  }

  def "does not skip empty classloader"() {
    setup:
    ClassLoader emptyLoader = new ClassLoader() {}
    expect:
    !ClassLoaderMatcher.skipClassLoader().matches(emptyLoader)
  }

  def "does not skip bootstrap classloader"() {
    expect:
    !ClassLoaderMatcher.skipClassLoader().matches(null)
  }

  def "AgentClassLoader class name is hardcoded in ClassLoaderMatcher"() {
    expect:
    AgentClassLoader.name == "io.opentelemetry.auto.bootstrap.AgentClassLoader"
  }

  def "ExporterClassLoader class name is hardcoded in ClassLoaderMatcher"() {
    expect:
    ExporterClassLoader.name == "io.opentelemetry.auto.tooling.ExporterClassLoader"
  }

  def "helper class names are hardcoded in Log Instrumentations"() {
    expect:
    LogContextScopeListener.name == "io.opentelemetry.auto.tooling.log.LogContextScopeListener"
  }
}
