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
  def loggingExporterJar = System.getProperty("loggingExporterJar")

  @Shared
  def jaegerExporterJar = System.getProperty("jaegerExporterJar")

  @Shared
  def jaegerDir = new File("${adapterRoot}/jaeger-adapter/build/libs")

  def "test jars exist"() {
    when:
    def file = new File(exporter)

    then:
    file != null
    
    where:
    exporter << [loggingExporterJar, jaegerExporterJar]
  }

  def "test exporter load"() {
    setup:
    def file = new File(exporter)
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
    jaegerExporterJar  | 'io.opentelemetry.auto.exporters.jaeger.JaegerExporterFactory'
    loggingExporterJar | 'io.opentelemetry.auto.exporters.loggingexporter.LoggingExporterFactory'
  }
}
