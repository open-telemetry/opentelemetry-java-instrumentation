/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import java.nio.charset.StandardCharsets

class Axis2JaxWsTest extends AbstractJaxWsTest {
  static {
    updateConfiguration()
  }

  static updateConfiguration() {
    // read default configuration file inside axis2 jar
    String configuration = Axis2JaxWsTest.getClassLoader().getResourceAsStream("axis2.xml").getText(StandardCharsets.UTF_8.name())

    // customize deployer so axis2 can find our services
    configuration = configuration.replace("org.apache.axis2.jaxws.framework.JAXWSDeployer", "test.CustomJaxWsDeployer")
    configuration = configuration.replace("<!--<parameter name=\"servicePath\">services</parameter>-->", "<parameter name=\"servicePath\">ws</parameter>")
    configuration = configuration.replace("<parameter name=\"useGeneratedWSDLinJAXWS\">false</parameter>", "<parameter name=\"useGeneratedWSDLinJAXWS\">true</parameter>")
    configuration = configuration.replace("<module ref=\"addressing\"/>", "")

    File configurationDirectory = new File("build/axis-conf/")
    configurationDirectory.mkdirs()
    new File(configurationDirectory, "axis2.xml").setText(configuration, StandardCharsets.UTF_8.name())
  }
}
