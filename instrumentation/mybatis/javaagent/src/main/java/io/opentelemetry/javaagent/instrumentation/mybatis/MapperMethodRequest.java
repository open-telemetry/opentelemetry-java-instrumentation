package io.opentelemetry.javaagent.instrumentation.mybatis;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class MapperMethodRequest {

  public static MapperMethodRequest create(String mapperName) {
    return new AutoValue_MapperMethodRequest(mapperName);
  }

  public abstract String getMapperName();
}
