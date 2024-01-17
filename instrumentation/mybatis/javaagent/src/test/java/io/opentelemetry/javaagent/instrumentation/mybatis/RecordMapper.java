package io.opentelemetry.javaagent.instrumentation.mybatis;

import org.apache.ibatis.annotations.Update;

public interface RecordMapper {
  @Update("update dummy_record set content = '3131223'")
  void updateRecord();
}
