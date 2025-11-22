plugins {
  id("otel.java-conventions")
}

dependencies {
  implementation("org.apache.iceberg:iceberg-core:1.8.1")
  implementation("org.apache.hadoop:hadoop-common:3.4.2")
  api(project(":testing-common"))
}
