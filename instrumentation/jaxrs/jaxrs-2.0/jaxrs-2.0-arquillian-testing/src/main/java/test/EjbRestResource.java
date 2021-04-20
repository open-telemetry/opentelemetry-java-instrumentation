/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test;

import javax.ejb.Stateless;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

@Path("/ejbHello")
@Stateless
public class EjbRestResource {

  @GET
  public String hello() {
    return "hello";
  }
}
