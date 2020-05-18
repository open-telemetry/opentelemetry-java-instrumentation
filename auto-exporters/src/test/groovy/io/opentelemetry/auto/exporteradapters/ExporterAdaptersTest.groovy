/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import io.opentelemetry.auto.tooling.ExporterClassLoader
import io.opentelemetry.sdk.contrib.auto.config.SpanExporterFactory
import spock.lang.Shared
import spock.lang.Specification

class ExporterAdaptersTest extends Specification {
  @Shared
  def projectVersion = System.getProperty("projectVersion")

  @Shared
  def adapterRoot = System.getProperty("adapterRoot")

  @Shared
  def otlpExporterJar = System.getProperty("otlpExporterJar")

  @Shared
  def jaegerExporterJar = System.getProperty("jaegerExporterJar")

  @Shared
  def loggingExporterJar = System.getProperty("loggingExporterJar")

  @Shared
  def jaegerDir = new File("${adapterRoot}/jaeger-adapter/build/libs")

  def "test jars exist"() {
    when:
    def file = new File(exporter)

    then:
    file != null

    where:
    exporter << [otlpExporterJar, jaegerExporterJar, loggingExporterJar]
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
    otlpExporterJar    | 'io.opentelemetry.auto.exporters.otlp.OtlpSpanExporterFactory'
    jaegerExporterJar  | 'io.opentelemetry.auto.exporters.jaeger.JaegerExporterFactory'
    loggingExporterJar | 'io.opentelemetry.auto.exporters.logging.LoggingExporterFactory'
  }
}
