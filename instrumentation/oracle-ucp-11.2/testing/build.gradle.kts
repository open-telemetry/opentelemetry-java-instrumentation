plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))
  implementation("org.testcontainers:oracle-free")

  compileOnly("com.oracle.database.jdbc:ucp:11.2.0.4")
}
