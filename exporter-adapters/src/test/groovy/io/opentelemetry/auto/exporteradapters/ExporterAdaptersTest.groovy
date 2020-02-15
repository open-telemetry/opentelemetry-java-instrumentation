import io.opentelemetry.auto.exportersupport.SpanExporterFactory
import io.opentelemetry.auto.tooling.ExporterClassLoader
import spock.lang.Shared
import spock.lang.Specification

class ExporterAdaptersTest extends Specification {
  @Shared
  def projectVersion = System.getProperty("projectVersion")

  def "test exporter load"() {
    setup:
    def file = new File("${exporter}-adapter/build/libs/${exporter}-adapter-${projectVersion}-all.jar")
    println file.toString() + " " + classname
    URL[] urls = [file.toURI().toURL()]
    def cl = new ExporterClassLoader(urls, this.getClass().getClassLoader())
    def sl = new ServiceLoader<SpanExporterFactory>(SpanExporterFactory.class, cl)

    when:
    def f = sl.iterator().next()
    println f.class.getName()

    then:
    file.exists()
    f != null
    f instanceof SpanExporterFactory
    f.getClass().getName() == classname

    where:
    exporter         || classname
    'jaeger'         || 'io.opentelemetry.auto.exporters.jaeger.JaegerExporterFactory'
    'dummy-exporter' || 'io.opentelemetry.auto.exporters.dummyexporter.DummySpanExporterFactory'
  }
}
