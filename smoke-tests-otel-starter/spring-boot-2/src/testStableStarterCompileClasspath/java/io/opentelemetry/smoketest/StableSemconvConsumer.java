/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;

class StableSemconvConsumer {

  Object urlFullAttribute() {
    return URL_FULL;
  }
}
