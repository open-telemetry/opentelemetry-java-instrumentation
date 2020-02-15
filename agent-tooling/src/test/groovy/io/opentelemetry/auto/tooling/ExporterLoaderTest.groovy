package io.opentelemetry.auto.tooling


import io.opentelemetry.auto.util.test.AgentSpecification
import io.opentelemetry.sdk.OpenTelemetrySdk

class ExporterLoaderTest extends AgentSpecification {
  def projectVersion = System.getProperty("projectVersion")
  def jarName = "../exporter-adapters/dummy-exporter-adapter/build/libs/dummy-exporter-adapter-${projectVersion}-all.jar"
  def tracer = OpenTelemetrySdk.getTracerFactory().get("test")

  def "test load exporter"() {
    when:
    def exporter = TracerInstaller.loadFromJar(jarName)

    then:
    exporter.getClass().getName() == "io.opentelemetry.auto.exporters.dummyexporter.DummyExporter"
  }
}
