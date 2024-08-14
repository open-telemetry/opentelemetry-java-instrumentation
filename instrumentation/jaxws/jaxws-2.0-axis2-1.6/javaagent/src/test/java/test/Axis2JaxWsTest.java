/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test;

import io.opentelemetry.javaagent.instrumentation.jaxws.v2_0.AbstractJaxWsTest;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

class Axis2JaxWsTest extends AbstractJaxWsTest {
  static {
    try {
      Axis2JaxWsTest.updateConfiguration();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  static File updateConfiguration() throws IOException {
    // read default configuration file inside axis2 jar
    String configuration = IOUtils
        .toString(Axis2JaxWsTest.class.getClassLoader().getResourceAsStream("axis2.xml"), StandardCharsets.UTF_8);

    // customize deployer so axis2 can find our services
    configuration = configuration.replace("org.apache.axis2.jaxws.framework.JAXWSDeployer", "test.CustomJaxWsDeployer");
    configuration = configuration.replace("<!--<parameter name=\"servicePath\">services</parameter>-->", "<parameter name=\"servicePath\">ws</parameter>");
    configuration = configuration.replace("<parameter name=\"useGeneratedWSDLinJAXWS\">false</parameter>", "<parameter name=\"useGeneratedWSDLinJAXWS\">true</parameter>");
    configuration = configuration.replace("<module ref=\"addressing\"/>", "");

    File configurationDirectory = new File("build/axis-conf/");
    configurationDirectory.mkdirs();
    File file = new File(configurationDirectory, "axis2.xml");
    try (OutputStreamWriter osw = new OutputStreamWriter(Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8)) {
      osw.write(configuration);
      osw.flush();
    }
    return file;
  }
}
