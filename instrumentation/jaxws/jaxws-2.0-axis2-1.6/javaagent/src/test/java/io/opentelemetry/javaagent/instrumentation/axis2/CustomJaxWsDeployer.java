/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.axis2;

import io.opentelemetry.javaagent.instrumentation.jaxws.v2_0.hello.HelloService;
import io.opentelemetry.javaagent.instrumentation.jaxws.v2_0.hello.HelloServiceImpl;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import org.apache.axis2.jaxws.framework.JAXWSDeployer;

// used in axis2.xml
public class CustomJaxWsDeployer extends JAXWSDeployer {

  @Override
  @SuppressWarnings("NonApiType") // errorprone bug that it doesn't recognize this is an override
  protected ArrayList<String> getClassesInWebInfDirectory(File file) {
    // help axis find our webservice classes
    return new ArrayList<>(
        Arrays.asList(HelloService.class.getName(), HelloServiceImpl.class.getName()));
  }
}
