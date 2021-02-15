/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

@ApplicationPath("/")
public class RestApplication extends Application {

  @Override
  public Set<Class<?>> getClasses() {
    return new HashSet<>(Arrays.asList(CdiRestResource.class, EjbRestResource.class));
  }
}
