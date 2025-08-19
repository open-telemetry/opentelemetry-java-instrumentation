plugins {
  id("otel.java-conventions")
  id("otel.javaagent-instrumentation")
}

repositories {
  mavenCentral()
}

dependencies {
  api(project(":instrumentation-api"))
  api(project(":instrumentation-api-incubator"))
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")
  compileOnly("com.clickhouse:clickhouse-client:0.5.0")
}

tasks.test {
  useJUnitPlatform()
}
