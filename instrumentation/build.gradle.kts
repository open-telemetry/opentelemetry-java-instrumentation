import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

// this project will run in isolation under the agent's classloader
plugins {
  id("otel.shadow-conventions")
  id("otel.java-conventions")
}

val instrumentationProjectTest = tasks.named("test")
val instrumentationProjectDependencies = dependencies

subprojects {
  val subProj = this
  plugins.withId("java") {
    instrumentationProjectTest.configure {
      dependsOn(subProj.tasks.named("test"))
    }
  }

  plugins.withId("otel.javaagent-instrumentation") {
    instrumentationProjectDependencies.run {
      implementation(project(subProj.path))
    }
  }
}

dependencies {
  compileOnly(project(":instrumentation-api"))
  compileOnly(project(":javaagent-api"))
  implementation(project(":javaagent-tooling"))
  implementation(project(":javaagent-extension-api"))
}

configurations {
  // exclude bootstrap dependencies from shadowJar
  implementation {
    exclude("org.slf4j")
    exclude("io.opentelemetry", "opentelemetry-api")
    exclude("io.opentelemetry", "opentelemetry-api-metrics")
    exclude("io.opentelemetry", "opentelemetry-semconv")
  }
}

tasks {
  named<ShadowJar>("shadowJar") {
    duplicatesStrategy = DuplicatesStrategy.FAIL

    dependencies {
      //These classes are added to bootstrap classloader by javaagent module
      exclude(project(":javaagent-bootstrap"))
      exclude(project(":instrumentation-api"))
      exclude(project(":javaagent-api"))
    }
  }

  register("listInstrumentations") {
    group = "Help"
    description = "List all available instrumentation modules"
    doFirst {
      subprojects
        .filter { it.plugins.hasPlugin("otel.muzzle-check") }
        .map { it.path }
        .forEach { println(it) }
    }
  }
}
