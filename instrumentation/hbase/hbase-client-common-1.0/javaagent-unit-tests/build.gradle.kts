plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:hbase:hbase-client-common-1.0:javaagent"))
  testImplementation("org.apache.hbase:hbase-client:1.0.0")
}
