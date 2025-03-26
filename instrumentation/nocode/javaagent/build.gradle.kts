plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    coreJdk()
  }
}

sourceSets {
  test {
    runtimeClasspath += files("${project.layout.buildDirectory}/classes/java/main")
  }
}

dependencies {
  bootstrap(project(":instrumentation:nocode:bootstrap"))
  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
  implementation(project(":sdk-autoconfigure-support"))
  compileOnly(project(":javaagent-tooling"))

  compileOnly("org.snakeyaml:snakeyaml-engine:2.9")

  implementation("org.apache.commons:commons-jexl3")

  add("codegen", project(":instrumentation:nocode:bootstrap"))
}

tasks.withType<Test>().configureEach {
  environment("OTEL_JAVA_INSTRUMENTATION_NOCODE_YML_FILE", "./src/test/config/nocode.yml")
}
