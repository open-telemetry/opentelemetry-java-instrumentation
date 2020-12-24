/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.test

import io.opentelemetry.javaagent.bootstrap.AgentClassLoader
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher
import io.opentelemetry.javaagent.tooling.ExporterClassLoader
import io.opentelemetry.javaagent.tooling.matcher.GlobalClassloaderIgnoresMatcher
import spock.lang.Specification

class ClassLoaderMatcherTest extends Specification {

  def "skips agent classloader"() {
    setup:
    URL root = new URL("file://")
    URLClassLoader agentLoader = new AgentClassLoader(root, null, null)
    expect:
    GlobalClassloaderIgnoresMatcher.skipClassLoader().matches(agentLoader)
  }

  def "skips exporter classloader"() {
    setup:
    URL url = new URL("file://")
    URLClassLoader exporterLoader = new ExporterClassLoader(url, null)
    expect:
    GlobalClassloaderIgnoresMatcher.skipClassLoader().matches(exporterLoader)
  }

  def "does not skip empty classloader"() {
    setup:
    ClassLoader emptyLoader = new ClassLoader() {}
    expect:
    !GlobalClassloaderIgnoresMatcher.skipClassLoader().matches(emptyLoader)
  }

  def "does not skip bootstrap classloader"() {
    expect:
    !GlobalClassloaderIgnoresMatcher.skipClassLoader().matches(null)
  }

  def "AgentClassLoader class name is hardcoded in ClassLoaderMatcher"() {
    expect:
    AgentClassLoader.name == "io.opentelemetry.javaagent.bootstrap.AgentClassLoader"
  }

  def "ExporterClassLoader class name is hardcoded in ClassLoaderMatcher"() {
    expect:
    ExporterClassLoader.name == "io.opentelemetry.javaagent.tooling.ExporterClassLoader"
  }
}
