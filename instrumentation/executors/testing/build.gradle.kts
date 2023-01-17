plugins {
  id("otel.java-conventions")
}

dependencies {
  api("org.junit.jupiter:junit-jupiter-api")

  implementation(project(":testing-common"))
}
