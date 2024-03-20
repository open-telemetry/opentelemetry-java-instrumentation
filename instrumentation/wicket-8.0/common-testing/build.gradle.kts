plugins {
  id("otel.java-conventions")
}

dependencies {
  implementation(project(":testing-common"))
  implementation("org.apache.wicket:wicket:8.0.0")
  implementation("org.jsoup:jsoup:1.13.1")
}
