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
  implementation("org.apache.commons:commons-compress:1.26.2")
  api(project(":testing-common"))
}
