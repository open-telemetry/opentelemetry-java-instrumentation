/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config

import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir
import spock.util.environment.RestoreSystemProperties

@RestoreSystemProperties
class ConfigurationFileTest extends Specification {

  @Rule
  public final EnvironmentVariables environmentVariables = new EnvironmentVariables()

  @TempDir
  @Shared
  public File tmpDir

  ConfigurationFileLoader loader = new ConfigurationFileLoader()

  def "should use env property"() {
    given:
    def path = createFile("config", "property1=val-env")
    environmentVariables.set("OTEL_JAVAAGENT_CONFIGURATION_FILE", path)

    when:
    def properties = loader.get()

    then:
    properties.get("property1") == "val-env"
  }

  def "should use system property"() {
    given:
    def path = createFile("config", "property1=val-sys")
    System.setProperty("otel.javaagent.configuration-file", path)

    when:
    def properties = loader.get()

    then:
    properties.get("property1") == "val-sys"
  }

  def "system property should take precedence over env property"() {
    given:
    def pathEnv = createFile("configEnv", "property1=val-env")
    def pathSys = createFile("configSys", "property1=val-sys")

    environmentVariables.set("OTEL_JAVAAGENT_CONFIGURATION_FILE", pathEnv)
    System.setProperty("otel.javaagent.configuration-file", pathSys)

    when:
    def properties = loader.get()

    then:
    properties.get("property1") == "val-sys"
  }


  def "should return empty properties if file does not exist"() {
    given:
    environmentVariables.set("OTEL_JAVAAGENT_CONFIGURATION_FILE", "somePath")

    when:
    def properties = loader.get()

    then:
    properties.isEmpty()
  }

  def "should return empty properties if property is not set"() {
    when:
    def properties = loader.get()

    then:
    properties.isEmpty()
  }

  def createFile(String name, String contents) {
    def file = new File(tmpDir, name)
    file.write(contents)
    return file.getAbsolutePath()
  }

}
