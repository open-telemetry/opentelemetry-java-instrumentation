import io.quarkus.bootstrap.model.ApplicationModel
import io.quarkus.bootstrap.model.gradle.impl.ModelParameterImpl
import io.quarkus.bootstrap.util.BootstrapUtils
import io.quarkus.gradle.tooling.GradleApplicationModelBuilder
import io.quarkus.runtime.LaunchMode
import kotlin.io.path.notExists
import kotlin.jvm.java

plugins {
  id("otel.javaagent-testing")

  id("io.opentelemetry.instrumentation.quarkus3") apply false
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}

// io.quarkus.platform:quarkus-bom is missing for 3.0.0.Final
var quarkusVersion = "3.0.1.Final"
if (findProperty("testLatestDeps") as Boolean) {
  quarkusVersion = "3.5.+"
}

dependencies {
  implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:$quarkusVersion"))
  // fails with junit 5.11.+
  implementation(enforcedPlatform("org.junit:junit-bom:5.10.3"))
  implementation("io.quarkus:quarkus-resteasy-reactive")

  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:quarkus-resteasy-reactive:javaagent"))
  testInstrumentation(project(":instrumentation:vertx:vertx-web-3.0:javaagent"))

  testImplementation(project(":instrumentation:quarkus-resteasy-reactive:common-testing"))
  testImplementation("io.quarkus:quarkus-junit5")
}

tasks.register("integrationTestClasses") {}

val quarkusTestBaseRuntimeClasspathConfiguration by configurations.creating {
  extendsFrom(configurations["testRuntimeClasspath"])
}

val quarkusTestCompileOnlyConfiguration by configurations.creating {
}

val testModelPath = layout.buildDirectory.file("quarkus-app-test-model.dat").get().asFile.toPath()

val buildModel = tasks.register("buildModel") {
  dependsOn(configurations.named("testRuntimeClasspath"))

  if (testModelPath.notExists()) {
    doLast {
      val modelParameter = ModelParameterImpl()
      modelParameter.mode = LaunchMode.TEST.toString()
      val model = GradleApplicationModelBuilder().buildAll(
        ApplicationModel::class.java.getName(),
        modelParameter,
        project
      )
      BootstrapUtils.serializeAppModel(model as ApplicationModel?, testModelPath)
    }
  }
  outputs.file(testModelPath)
}

tasks {
  test {
    dependsOn(buildModel)

    systemProperty("quarkus-internal-test.serialized-app-model.path", testModelPath.toString())
  }

  if (findProperty("denyUnsafe") as Boolean) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}
