/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.tapestry.components;

import org.apache.tapestry5.BindingConstants;
import org.apache.tapestry5.ComponentResources;
import org.apache.tapestry5.annotations.Parameter;
import org.apache.tapestry5.annotations.Property;
import org.apache.tapestry5.ioc.annotations.Inject;

@SuppressWarnings("unused")
public class Layout {
  @Inject private ComponentResources resources;

  @Property
  @Parameter(required = true, defaultPrefix = BindingConstants.LITERAL)
  private String title;
}
