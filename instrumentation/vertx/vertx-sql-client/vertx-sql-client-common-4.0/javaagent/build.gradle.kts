plugins {
  id("otel.javaagent-instrumentation")
}

testing.suites {
  register<JvmTestSuite>("unitTests") {
    dependencies {
      implementation(project())
      implementation(project(":instrumentation-api-incubator"))
      implementation("io.vertx:vertx-db2-client:4.0.0")
      implementation("io.vertx:vertx-mysql-client:4.0.0")
      implementation("io.vertx:vertx-mssql-client:4.0.0")
      implementation("io.vertx:vertx-sql-client:4.0.0")
    }
  }
}

tasks.check {
  dependsOn(testing.suites)
}

dependencies {
  compileOnly("io.vertx:vertx-sql-client:4.0.0")
  compileOnly("io.vertx:vertx-codegen:4.0.0")
}
