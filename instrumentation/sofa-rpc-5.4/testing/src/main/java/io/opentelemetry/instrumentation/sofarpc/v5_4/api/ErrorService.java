/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofarpc.v5_4.api;

public interface ErrorService {
  String throwException();

  String throwBusinessException();

  String timeout();
}
