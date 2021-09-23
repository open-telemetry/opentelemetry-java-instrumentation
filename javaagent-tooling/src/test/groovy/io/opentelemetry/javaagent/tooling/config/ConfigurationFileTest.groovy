/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config

import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import org.junit.contrib.java.lang.system.RestoreSystemProperties
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class ConfigurationFileTest extends Specification {

  @Rule
  public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties()
  @Rule
  public final EnvironmentVariables environmentVariables = new EnvironmentVariables()
  @Rule
  public final TemporaryFolder tmpFolder = new TemporaryFolder()

  def "should use env property"() {
    given:
    def path = createFile("config", "property1=val-env")
    environmentVariables.set(ConfigInitializer.CONFIGURATION_FILE_ENV_VAR, path)

    when:
    def properties = ConfigInitializer.loadConfigurationFile()

    then:
    properties.get("property1") == "val-env"
  }

  def "should use system property"() {
    given:
    def path = createFile("config", "property1=val-sys")
    System.setProperty(ConfigInitializer.CONFIGURATION_FILE_PROPERTY, path)

    when:
    def properties = ConfigInitializer.loadConfigurationFile()

    then:
    properties.get("property1") == "val-sys"
  }

  def "system property should take precedence over env property"() {
    given:
    def pathEnv = createFile("configEnv", "property1=val-env")
    def pathSys = createFile("configSys", "property1=val-sys")

    environmentVariables.set(ConfigInitializer.CONFIGURATION_FILE_ENV_VAR, pathEnv)
    System.setProperty(ConfigInitializer.CONFIGURATION_FILE_PROPERTY, pathSys)

    when:
    def properties = ConfigInitializer.loadConfigurationFile()

    then:
    properties.get("property1") == "val-sys"
  }


  def "should return empty properties if file does not exist"() {
    given:
    environmentVariables.set(ConfigInitializer.CONFIGURATION_FILE_ENV_VAR, "somePath")

    when:
    def properties = ConfigInitializer.loadConfigurationFile()

    then:
    !properties.propertyNames().hasMoreElements()
  }

  def "should return empty properties if property is not set"() {
    when:
    def properties = ConfigInitializer.loadConfigurationFile()

    then:
    !properties.propertyNames().hasMoreElements()
  }

  def createFile(String name, String contents) {
    def file = tmpFolder.newFile(name)
    file.write(contents)
    return file.getAbsolutePath()
  }

}
