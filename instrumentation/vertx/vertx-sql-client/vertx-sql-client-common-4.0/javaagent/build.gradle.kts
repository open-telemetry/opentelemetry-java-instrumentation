import io.opentelemetry.instrumentation.gradle.unitTests

plugins {
  id("otel.javaagent-instrumentation")
}

testing.suites {
  unitTests {
    dependencies {
      implementation("io.vertx:vertx-db2-client:4.0.0")
      implementation("io.vertx:vertx-mysql-client:4.0.0")
      implementation("io.vertx:vertx-mssql-client:4.0.0")
      implementation("io.vertx:vertx-sql-client:4.0.0")
    }
  }
}

dependencies {
  compileOnly("io.vertx:vertx-sql-client:4.0.0")
  compileOnly("io.vertx:vertx-codegen:4.0.0")
}
