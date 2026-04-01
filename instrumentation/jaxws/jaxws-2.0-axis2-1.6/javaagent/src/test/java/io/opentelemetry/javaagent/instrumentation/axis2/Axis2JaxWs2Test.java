/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.axis2;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import io.opentelemetry.javaagent.instrumentation.jaxws.v2_0.AbstractJaxWs2Test;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
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
    String configuration;
    try (InputStream inputStream =
        requireNonNull(
            Axis2JaxWs2Test.class.getClassLoader().getResourceAsStream("axis2.xml"), "axis2.xml")) {
      configuration = IOUtils.toString(inputStream, UTF_8);
    }

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
    Files.createDirectories(configurationDirectory.toPath());
    FileUtils.writeStringToFile(
        new File(configurationDirectory, "axis2.xml"), configuration, UTF_8);
  }
}
