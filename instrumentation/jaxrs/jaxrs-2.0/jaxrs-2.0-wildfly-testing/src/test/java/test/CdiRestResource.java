/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test;

import javax.inject.Named;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

@Path("/cdiHello")
@Named("cdiHello")
public class CdiRestResource {

  @GET
  public String hello() {
    return "hello";
  }
}
