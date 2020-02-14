package io.opentelemetry.auto.tooling


import io.opentelemetry.auto.util.test.AgentSpecification
import io.opentelemetry.sdk.OpenTelemetrySdk

class ExporterLoaderTest extends AgentSpecification {
  def jarName = "../dummy-exporter/build/libs/dummy-exporter-0.1.0-all.jar"
  def tracer = OpenTelemetrySdk.getTracerFactory().get("test")

  def "test load exporter"() {
    when:
    def exporter = TracerInstaller.loadFromJar(jarName)

    then:
    exporter.getClass().getName() == "io.opentelemetry.auto.dummyexporter.DummyExporter"
  }
}
