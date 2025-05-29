/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.axis2;

import io.opentelemetry.javaagent.instrumentation.jaxws.v2_0.AbstractJaxWs2Test;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

class Axis2JaxWs2Test extends AbstractJaxWs2Test {
  static {
    try {
      updateConfiguration();
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static void updateConfiguration() throws IOException {
    // read default configuration file inside axis2 jar
    String configuration =
        IOUtils.toString(
            Axis2JaxWs2Test.class.getClassLoader().getResourceAsStream("axis2.xml"),
            StandardCharsets.UTF_8);

    // customize deployer so axis2 can find our services
    configuration =
        configuration.replace(
            "org.apache.axis2.jaxws.framework.JAXWSDeployer", CustomJaxWsDeployer.class.getName());
    configuration =
        configuration.replace(
            "<!--<parameter name=\"servicePath\">services</parameter>-->",
            "<parameter name=\"servicePath\">ws</parameter>");
    configuration =
        configuration.replace(
            "<parameter name=\"useGeneratedWSDLinJAXWS\">false</parameter>",
            "<parameter name=\"useGeneratedWSDLinJAXWS\">true</parameter>");
    configuration = configuration.replace("<module ref=\"addressing\"/>", "");

    File configurationDirectory = new File("build/axis-conf/");
    configurationDirectory.mkdirs();
    FileUtils.writeStringToFile(
        new File(configurationDirectory, "axis2.xml"), configuration, StandardCharsets.UTF_8);
  }
}
