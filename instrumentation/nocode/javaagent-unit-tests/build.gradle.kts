plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:nocode:javaagent"))
  testImplementation(project(":instrumentation:nocode:bootstrap"))
  testImplementation("org.apache.commons:commons-jexl3")
}
