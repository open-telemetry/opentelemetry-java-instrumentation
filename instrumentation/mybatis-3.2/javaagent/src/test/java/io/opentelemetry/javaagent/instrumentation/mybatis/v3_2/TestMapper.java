/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mybatis.v3_2;

import org.apache.ibatis.annotations.Select;

public interface TestMapper {

  @Select("SELECT 1")
  int select();
}
