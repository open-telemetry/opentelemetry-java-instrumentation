/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import org.apache.axis2.jaxws.framework.JAXWSDeployer;

// used in axis2.xml
public class CustomJaxWsDeployer extends JAXWSDeployer {

  @Override
  protected ArrayList<String> getClassesInWebInfDirectory(File file) {
    // help axis find our webservice classes
    return new ArrayList<>(Arrays.asList("hello.HelloService", "hello.HelloServiceImpl"));
  }
}
