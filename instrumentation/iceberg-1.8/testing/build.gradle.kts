plugins {
  id("otel.java-conventions")
}

dependencies {
  implementation("org.apache.iceberg:iceberg-core:1.8.1")
  implementation("org.apache.iceberg:iceberg-core:1.8.1") {
    artifact {
      classifier = "tests"
    }
  }
  api(project(":testing-common"))
}
