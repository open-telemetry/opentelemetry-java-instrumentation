/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.tapestry.pages;

import org.apache.tapestry5.annotations.InjectPage;

public class Index {

  @InjectPage private Other other;

  Object onActionFromStart() {
    return other;
  }

  Object onActionFromException() {
    throw new IllegalStateException("expected");
  }
}
