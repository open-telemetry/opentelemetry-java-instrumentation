import io.quarkus.bootstrap.model.ApplicationModel
import io.quarkus.bootstrap.model.gradle.impl.ModelParameterImpl
import io.quarkus.bootstrap.util.BootstrapUtils
import io.quarkus.gradle.tooling.GradleApplicationModelBuilder
import io.quarkus.runtime.LaunchMode
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import kotlin.io.path.notExists

plugins {
  id("otel.javaagent-testing")

  id("io.opentelemetry.instrumentation.quarkus-2.0") apply false
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}

dependencies {
  implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:2.16.7.Final"))
  // fails with junit 5.11.+
  implementation(enforcedPlatform("org.junit:junit-bom:5.10.3"))
  implementation("io.quarkus:quarkus-resteasy-reactive")

  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:quarkus-resteasy-reactive:javaagent"))
  testInstrumentation(project(":instrumentation:vertx:vertx-web-3.0:javaagent"))

  testImplementation(project(":instrumentation:quarkus-resteasy-reactive:quarkus-common-testing"))
  testImplementation("io.quarkus:quarkus-junit5")
}

tasks.register("integrationTestClasses") {}

val quarkusTestBaseRuntimeClasspathConfiguration by configurations.creating {
  extendsFrom(configurations["testRuntimeClasspath"])
}

val quarkusTestCompileOnlyConfiguration by configurations.creating {
}

val testModelPath = layout.buildDirectory.file("quarkus-app-test-model.dat")

val buildModel = if (findProperty("skipTests") as String? != "true") {
  tasks.register<BuildModelTask>("buildModel") {
    projectRef = project
    runtimeClasspath.from(configurations.named("testRuntimeClasspath"))
    outputFile.set(testModelPath)

    onlyIf { outputFile.get().asFile.toPath().notExists() }
  }
} else {
  null
}

tasks {
  test {
    if (buildModel != null) {
      dependsOn(buildModel)
    }

    systemProperty("quarkus-internal-test.serialized-app-model.path", testModelPath.get().asFile.toString())
  }

  if (findProperty("denyUnsafe") as Boolean) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}

abstract class BuildModelTask : DefaultTask() {

  @get:Internal
  @Transient
  var projectRef: Project? = null

  @get:Classpath
  abstract val runtimeClasspath: ConfigurableFileCollection

  @get:OutputFile
  abstract val outputFile: RegularFileProperty

  init {
    notCompatibleWithConfigurationCache(
      "Quarkus GradleApplicationModelBuilder.buildAll() requires Project reference"
    )
  }

  @TaskAction
  fun buildModel() {
    val modelPath = outputFile.get().asFile.toPath()
    val modelParameter = ModelParameterImpl()
    modelParameter.mode = LaunchMode.TEST.toString()
    val model = GradleApplicationModelBuilder().buildAll(
      ApplicationModel::class.java.getName(),
      modelParameter,
      checkNotNull(projectRef)
    )
    BootstrapUtils.serializeAppModel(model as ApplicationModel?, modelPath)
  }
}
