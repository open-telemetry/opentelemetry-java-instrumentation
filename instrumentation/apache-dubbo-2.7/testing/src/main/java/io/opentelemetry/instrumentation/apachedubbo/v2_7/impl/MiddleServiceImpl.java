/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7.impl;

import io.opentelemetry.instrumentation.apachedubbo.v2_7.api.MiddleService;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.rpc.service.GenericService;

public class MiddleServiceImpl implements MiddleService {

  private final ReferenceConfig<?> referenceConfig;

  public MiddleServiceImpl(ReferenceConfig<?> referenceConfig) {
    this.referenceConfig = referenceConfig;
  }

  @Override
  public String hello(String hello) {
    GenericService genericService = (GenericService) referenceConfig.get();
    return genericService
        .$invoke("hello", new String[] {String.class.getName()}, new Object[] {hello})
        .toString();
  }
}
