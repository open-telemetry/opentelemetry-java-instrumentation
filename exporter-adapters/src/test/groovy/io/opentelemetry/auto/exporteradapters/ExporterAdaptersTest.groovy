import io.opentelemetry.auto.exportersupport.SpanExporterFactory
import io.opentelemetry.auto.tooling.ExporterClassLoader
import spock.lang.Shared
import spock.lang.Specification

class ExporterAdaptersTest extends Specification {
  @Shared
  def projectVersion = System.getProperty("projectVersion")
  def adapterRoot = System.getProperty("adapterRoot")

  def "test exporter load"() {
    setup:
    def file = new File("${adapterRoot}/${exporter}-adapter/build/libs/${exporter}-adapter-${projectVersion}-all.jar")
    println "Attempting to load ${file.toString()} for ${classname}"
    assert file.exists(): "${file.toString()} does not exist"
    URL[] urls = [file.toURI().toURL()]
    def cl = new ExporterClassLoader(urls, this.getClass().getClassLoader())
    def sl = ServiceLoader.load(SpanExporterFactory.class, cl)

    when:
    def f = sl.iterator().next()
    println f.class.getName()

    then:
    f != null
    f instanceof SpanExporterFactory
    f.getClass().getName() == classname

    where:
    exporter         || classname
    'jaeger'         || 'io.opentelemetry.auto.exporters.jaeger.JaegerExporterFactory'
    'dummy-exporter' || 'io.opentelemetry.auto.exporters.dummyexporter.DummySpanExporterFactory'
  }
}
