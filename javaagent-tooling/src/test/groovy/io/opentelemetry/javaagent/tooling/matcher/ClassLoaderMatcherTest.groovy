/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.matcher

import io.opentelemetry.javaagent.bootstrap.AgentClassLoader
import io.opentelemetry.javaagent.spi.IgnoreMatcherProvider
import io.opentelemetry.javaagent.tooling.ExporterClassLoader
import spock.lang.Specification

class ClassLoaderMatcherTest extends Specification {

  private final IgnoreMatcherProvider matcherProvider = [classloader: { cl -> IgnoreMatcherProvider.Result.DEFAULT }] as IgnoreMatcherProvider

  def "skips agent classloader"() {
    setup:
    URLClassLoader agentLoader = new AgentClassLoader(null, null, null)
    expect:
    GlobalClassloaderIgnoresMatcher.skipClassLoader(matcherProvider).matches(agentLoader)
  }

  def "skips exporter classloader"() {
    setup:
    URL url = new URL("file://")
    URLClassLoader exporterLoader = new ExporterClassLoader(url, null)
    expect:
    GlobalClassloaderIgnoresMatcher.skipClassLoader(matcherProvider).matches(exporterLoader)
  }

  def "does not skip empty classloader"() {
    setup:
    ClassLoader emptyLoader = new ClassLoader() {}
    expect:
    !GlobalClassloaderIgnoresMatcher.skipClassLoader(matcherProvider).matches(emptyLoader)
  }

  def "does not skip bootstrap classloader"() {
    expect:
    !GlobalClassloaderIgnoresMatcher.skipClassLoader(matcherProvider).matches(null)
  }

  def "skip bootstrap classloader"() {
    IgnoreMatcherProvider skipBootstrapClMatcherProvider = [classloader: { cl -> cl == null ? IgnoreMatcherProvider.Result.IGNORE : IgnoreMatcherProvider.Result.DEFAULT }] as IgnoreMatcherProvider
    expect:
    GlobalClassloaderIgnoresMatcher.skipClassLoader(skipBootstrapClMatcherProvider).matches(null)
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
