plugins {
  id("otel.java-conventions")
}

dependencies {
  implementation("org.apache.iceberg:iceberg-core:1.8.1")
  // The following dependency allows use of Iceberg test classes such as TestTables,
  // which are not published by default.
  implementation("org.apache.iceberg:iceberg-core:1.8.1") {
    artifact {
      classifier = "tests"
    }
  }
  api(project(":testing-common"))
}

tasks {
  javadoc {
    enabled = false
  }
}
