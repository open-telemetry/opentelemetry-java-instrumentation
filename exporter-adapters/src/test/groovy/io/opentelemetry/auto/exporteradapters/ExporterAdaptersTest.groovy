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

  def "test adapter build"() {
    // This test was added to check for unexpected behavior of the CircleCI build.
    setup:
    assert jaegerDir.exists(): "${jaegerDir.toString()} does not exist"
    assert jaegerDir.list().length > 0: "${jaegerDir.toString()} is empty"

    expect:
    file.startsWith("jaeger-adapter-${projectVersion}")

    where:
    file << jaegerDir.list()
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
