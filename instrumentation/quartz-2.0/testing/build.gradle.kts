plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  api("org.quartz-scheduler:quartz:2.0.0")
}
