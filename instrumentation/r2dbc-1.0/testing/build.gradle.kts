plugins {
  id("otel.java-conventions")
}

dependencies {
  implementation(project(":testing-common"))

  implementation("io.r2dbc:r2dbc-spi:1.0.0.RELEASE")

  implementation(project(":instrumentation-api-incubator"))
  implementation("org.testcontainers:junit-jupiter")
  compileOnly("io.projectreactor:reactor-core:3.4.12")

  runtimeOnly("dev.miku:r2dbc-mysql:0.8.2.RELEASE")
  runtimeOnly("org.mariadb:r2dbc-mariadb:1.1.3")
  runtimeOnly("org.postgresql:r2dbc-postgresql:1.0.1.RELEASE")
}
