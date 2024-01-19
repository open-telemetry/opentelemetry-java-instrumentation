/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mybatis.v3_2;

import org.apache.ibatis.annotations.Update;

public interface RecordMapper {

  @Update("CREATE TABLE dummy_record (id INT PRIMARY KEY, content VARCHAR(255))")
  void updateRecord();
}
