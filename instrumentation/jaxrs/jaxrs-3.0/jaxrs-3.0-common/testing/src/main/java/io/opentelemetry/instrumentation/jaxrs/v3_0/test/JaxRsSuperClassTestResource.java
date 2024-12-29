/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jaxrs.v3_0.test;

import jakarta.ws.rs.Path;

@Path("test-resource-super")
public class JaxRsSuperClassTestResource extends JaxRsSuperClassTestResourceSuper {}
