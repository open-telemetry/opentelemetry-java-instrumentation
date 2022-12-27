/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.javaagent.bootstrap.HelperResources
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.stream.Collectors
import org.apache.catalina.WebResource
import org.apache.catalina.WebResourceRoot
import org.apache.catalina.loader.ParallelWebappClassLoader
import spock.lang.Shared

class TomcatClassloadingTest extends AgentInstrumentationSpecification {

  @Shared
  ParallelWebappClassLoader classloader

  def setupSpec() {
    WebResourceRoot resources = Mock(WebResourceRoot) {
      getResource(_) >> Mock(WebResource)
      getClassLoaderResource(_) >> Mock(WebResource)
      listResources(_) >> []
      // Looks like 9.x.x needs this one:
      getResources(_) >> []
      getClassLoaderResources(_) >> []
    }
    def parentClassLoader = new ClassLoader(null) {
    }
    classloader = new ParallelWebappClassLoader(parentClassLoader)
    classloader.setResources(resources)
    classloader.init()
    classloader.start()
  }

  def "tomcat class loading delegates to parent for agent classes"() {
    expect:
    // If instrumentation didn't work this would blow up with NPE due to incomplete resources mocking
    classloader.loadClass("io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge")
  }

  def "test resource injection"() {
    setup:
    def tmpFile = Files.createTempFile("hello", "tmp")
    Files.write(tmpFile, "hello".getBytes(StandardCharsets.UTF_8))
    def url = tmpFile.toUri().toURL()
    HelperResources.register(classloader, "hello.txt", Arrays.asList(url))

    expect:
    classloader.getResource("hello.txt") != null

    and:
    def resources = classloader.getResources("hello.txt")
    resources != null
    resources.hasMoreElements()

    and:
    def inputStream = classloader.getResourceAsStream("hello.txt")
    inputStream != null
    String text = new BufferedReader(
      new InputStreamReader(inputStream, StandardCharsets.UTF_8))
      .lines()
      .collect(Collectors.joining("\n"))
    text == "hello"

    cleanup:
    inputStream?.close()
  }
}
