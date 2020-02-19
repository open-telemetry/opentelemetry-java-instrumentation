import io.opentelemetry.auto.exportersupport.SpanExporterFactory
import io.opentelemetry.auto.tooling.ExporterClassLoader
import spock.lang.Shared
import spock.lang.Specification

class ExporterAdaptersTest extends Specification {
  @Shared
  def projectVersion = System.getProperty("projectVersion")

  @Shared
  def adapterRoot = System.getProperty("adapterRoot")

  @Shared
  def jaegerDir = new File("${adapterRoot}/jaeger-adapter/build/libs")

  def "test dirs exist"() {
    when:
    def dir = new File("${adapterRoot}/${exporter}-adapter/build/libs")

    then:
    dir != null
    dir.exists()
    dir.list() != null
    dir.list().length > 0

    where:
    exporter << ['jaeger', 'logging-exporter']
  }

  def "test exporter load"() {
    setup:
    def file = new File("${adapterRoot}/${exporter}-adapter/build/libs/${exporter}-adapter-${projectVersion}-all.jar")
    println "Attempting to load ${file.toString()} for ${classname}"
    assert file.exists(): "${file.toString()} does not exist"
    URL[] urls = [file.toURI().toURL()]
    def classLoader = new ExporterClassLoader(urls, this.getClass().getClassLoader())
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
    'jaeger'           | 'io.opentelemetry.auto.exporters.jaeger.JaegerExporterFactory'
    'logging-exporter' | 'io.opentelemetry.auto.exporters.loggingexporter.LoggingExporterFactory'
  }
}
