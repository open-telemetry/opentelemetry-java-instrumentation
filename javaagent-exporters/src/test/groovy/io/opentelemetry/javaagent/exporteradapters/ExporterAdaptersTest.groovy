/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.javaagent.spi.exporter.SpanExporterFactory
import io.opentelemetry.javaagent.tooling.ExporterClassLoader
import spock.lang.Shared
import spock.lang.Specification

class ExporterAdaptersTest extends Specification {

  @Shared
  def otlpExporterJar = System.getProperty("otlpExporterJar")

  @Shared
  def jaegerExporterJar = System.getProperty("jaegerExporterJar")

  @Shared
  def loggingExporterJar = System.getProperty("loggingExporterJar")

  @Shared
  def zipkinExporterJar = System.getProperty("zipkinExporterJar")

  def "test jars exist"() {
    when:
    def file = new File(exporter)

    then:
    file != null

    where:
    exporter << [otlpExporterJar, jaegerExporterJar, loggingExporterJar, zipkinExporterJar]
  }

  def "test exporter load"() {
    setup:
    def file = new File(exporter)
    println "Attempting to load ${file.toString()} for ${classname}"
    assert file.exists(): "${file.toString()} does not exist"
    def classLoader = new ExporterClassLoader(file.toURI().toURL(), this.getClass().getClassLoader())
    def serviceLoader = ServiceLoader.load(SpanExporterFactory, classLoader)

    when:
    def f = serviceLoader.iterator().next()
    println f.class.getName()

    then:
    f != null
    f instanceof SpanExporterFactory
    f.getClass().getName() == classname

    where:
    exporter           | classname
    otlpExporterJar    | 'io.opentelemetry.javaagent.exporters.otlp.OtlpSpanExporterFactory'
    jaegerExporterJar  | 'io.opentelemetry.javaagent.exporters.jaeger.JaegerExporterFactory'
    loggingExporterJar | 'io.opentelemetry.javaagent.exporters.logging.LoggingExporterFactory'
    zipkinExporterJar  | 'io.opentelemetry.javaagent.exporters.zipkin.ZipkinExporterFactory'
  }
}
