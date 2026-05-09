/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package hello;

import org.apache.wicket.request.resource.CharSequenceResource;
import org.apache.wicket.request.resource.IResource;
import org.apache.wicket.request.resource.ResourceReference;

public class HelloResource extends ResourceReference {
  private static final long serialVersionUID = 1L;

  public HelloResource() {
    super("hello-resource");
  }

  @Override
  public IResource getResource() {
    return new CharSequenceResource("text/plain", "hello resource");
  }
}
