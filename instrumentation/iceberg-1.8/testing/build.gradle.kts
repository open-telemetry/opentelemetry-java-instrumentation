plugins {
  id("otel.java-conventions")
}

dependencies {
  implementation("org.apache.iceberg:iceberg-core:1.8.1")
  // The follwoing dependency allows us to use the following Iceberg test classes TestTables and TestTable
  // which are not published by default
  implementation("org.apache.iceberg:iceberg-core:1.8.1") {
    artifact {
      classifier = "tests"
    }
  }
  api(project(":testing-common"))
}
