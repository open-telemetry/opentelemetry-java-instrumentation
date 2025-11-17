import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage

plugins {
  id("otel.spotless-conventions")

  id("com.bmuschko.docker-remote-api")
}

data class ImageTarget(
  val version: List<String>,
  val vm: List<String>,
  val jdk: List<String>,
  val args: Map<String, String> = emptyMap(),
  val war: String = "servlet-3.0",
  val windows: Boolean = true
)

val extraTag = findProperty("extraTag")
  ?: java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd.HHmmSS").format(java.time.LocalDateTime.now())

val latestJava = "25" // renovate(java-version)

// Each line under appserver describes one matrix of (version x vm x jdk), dockerfile key overrides
// Dockerfile name, args key passes raw arguments to docker build
val targets = mapOf(
  "jetty" to listOf(
    ImageTarget(listOf("9.4.58"), listOf("hotspot", "openj9"), listOf("8", "11", "17", "21", latestJava), mapOf("sourceVersion" to "9.4.58.v20250814")),
    ImageTarget(listOf("10.0.26"), listOf("hotspot", "openj9"), listOf("11", "17", "21", latestJava), mapOf("sourceVersion" to "10.0.26")),
    ImageTarget(listOf("11.0.26"), listOf("hotspot", "openj9"), listOf("11", "17", "21", latestJava), mapOf("sourceVersion" to "11.0.26"), "servlet-5.0"),
    ImageTarget(listOf("12.0.28"), listOf("hotspot", "openj9"), listOf("17", "21", latestJava), mapOf("sourceVersion" to "12.0.28"), "servlet-5.0"),
  ),
  "liberty" to listOf(
    ImageTarget(listOf("20.0.0.12"), listOf("hotspot", "openj9"), listOf("8", "11"), mapOf("release" to "2020-11-11_0736")),
    ImageTarget(listOf("21.0.0.12"), listOf("hotspot", "openj9"), listOf("8", "11", "17"), mapOf("release" to "2021-11-17_1256")),
    // Java 19 is not supported until 22.0.0.10
    ImageTarget(listOf("22.0.0.12"), listOf("hotspot", "openj9"), listOf("8", "11", "17"), mapOf("release" to "22.0.0.12")),
    // Java 21 is not supported until 23.0.0.3 - despite that only 20 seems to work
    ImageTarget(listOf("23.0.0.12"), listOf("hotspot", "openj9"), listOf("8", "11", "17", "20"), mapOf("release" to "23.0.0.12")),
  ),
  "payara" to listOf(
    ImageTarget(listOf("5.2020.6", "5.2021.8"), listOf("hotspot", "openj9"), listOf("8", "11")),
    // Test application is not deployed when server is sarted with hotspot jdk version 21
    ImageTarget(listOf("6.2023.12"), listOf("hotspot"), listOf("11", "17"), war = "servlet-5.0"),
    ImageTarget(listOf("6.2023.12"), listOf("openj9"), listOf("11", "17", "21"), war = "servlet-5.0")
  ),
  "tomcat" to listOf(
    ImageTarget(listOf("7.0.109"), listOf("hotspot", "openj9"), listOf("8"), mapOf("majorVersion" to "7")),
    ImageTarget(listOf("8.5.98"), listOf("hotspot", "openj9"), listOf("8", "11", "17", "21", latestJava), mapOf("majorVersion" to "8")),
    ImageTarget(listOf("9.0.111"), listOf("hotspot", "openj9"), listOf("8", "11", "17", "21", latestJava), mapOf("majorVersion" to "9")),
    ImageTarget(listOf("10.1.48"), listOf("hotspot", "openj9"), listOf("11", "17", "21", latestJava), mapOf("majorVersion" to "10"), "servlet-5.0"),
  ),
  "tomee" to listOf(
    ImageTarget(listOf("7.0.9", "7.1.4"), listOf("hotspot", "openj9"), listOf("8")),
    ImageTarget(listOf("8.0.16"), listOf("hotspot", "openj9"), listOf("8", "11", "17", "21", latestJava)),
    ImageTarget(listOf("9.1.3"), listOf("hotspot", "openj9"), listOf("11", "17", "21", latestJava), war = "servlet-5.0"),
  ),
  "websphere" to listOf(
    ImageTarget(listOf("8.5.5.22", "9.0.5.14"), listOf("openj9"), listOf("8"), windows = false),
  ),
  "wildfly" to listOf(
    ImageTarget(listOf("13.0.0.Final"), listOf("hotspot", "openj9"), listOf("8")),
    ImageTarget(
      listOf("17.0.1.Final", "21.0.0.Final"),
      listOf("hotspot", "openj9"),
      listOf("8", "11", "17", "21")
    ),
    ImageTarget(
      listOf("28.0.1.Final", "29.0.1.Final", "30.0.1.Final"),
      listOf("hotspot", "openj9"),
      listOf("11", "17", "21"),
      war = "servlet-5.0"
    ),
  ),
)

val matrix = mutableListOf<String>()

tasks {
  val buildLinuxTestImages by registering {
    group = "build"
    description = "Builds all Linux Docker images for the test matrix"
  }

  val buildWindowsTestImages by registering {
    group = "build"
    description = "Builds all Windows Docker images for the test matrix"
  }

  val pushMatrix by registering(DockerPushImage::class) {
    mustRunAfter(buildLinuxTestImages)
    mustRunAfter(buildWindowsTestImages)
    group = "publishing"
    description = "Push all Docker images for the test matrix"
    images.set(matrix)
  }

  createDockerTasks(buildLinuxTestImages, false)
  createDockerTasks(buildWindowsTestImages, true)

  val printSmokeTestsConfigurations by registering {
    doFirst {
      for ((server, matrices) in targets) {
        val smokeTestServer = findProperty("smokeTestServer")
        if (smokeTestServer != null && server != smokeTestServer) {
          continue
        }
        println(server)
        val serverName = server.replaceFirstChar(Char::uppercase)
        for (entry in matrices) {
          for (version in entry.version) {
            val dotIndex = version.indexOf('.')
            val majorVersion = if (dotIndex != -1) version.substring(0, dotIndex) else version
            for (jdk in entry.jdk) {
              for (vm in entry.vm) {
                println("@AppServer(version = \"$version\", jdk = \"$jdk${if (vm == "hotspot") "" else "-openj9"}\")")
                println("class ${serverName}${majorVersion}Jdk${jdk}${if (vm == "hotspot") "" else "Openj9"} extends ${serverName}SmokeTest {")
                println("}")
              }
            }
          }
        }
      }
    }
  }
}

fun configureImage(
  parentTask: TaskProvider<out Task>,
  server: String,
  dockerfile: String,
  version: String,
  vm: String,
  jdk: String,
  warProject: String,
  args: Map<String, String>,
  isWindows: Boolean
): String {
  // Using separate build directory for different image
  val dockerWorkingDir = layout.buildDirectory.dir("docker-$server-$version-jdk$jdk-$vm-$warProject")
  val dockerFileName = "$dockerfile.${if (isWindows) "windows." else ""}dockerfile"
  val platformSuffix = if (isWindows) "-windows" else ""

  val prepareTask = tasks.register<Copy>("${server}ImagePrepare-$version-jdk$jdk-$vm$platformSuffix") {
    val warTask = project(":smoke-tests:images:servlet:$warProject").tasks.named<War>("war")
    dependsOn(warTask)
    into(dockerWorkingDir)
    from("src/$dockerFileName")
    from("src/main/docker/$server")
    from(warTask.get().archiveFile) {
      rename { "app.war" }
    }
  }

  val repo = System.getenv("GITHUB_REPOSITORY") ?: "open-telemetry/opentelemetry-java-instrumentation"
  val vmSuffix = if (vm == "hotspot") "" else "-$vm"
  val image = "ghcr.io/$repo/smoke-test-servlet-$server:$version-jdk$jdk$vmSuffix$platformSuffix-$extraTag"

  val jdkImage = if (vm == "hotspot") {
    if (jdk == "26-ea") {
      // "The only tags which will continue to receive updates beyond July 2022 will be Early Access
      // builds (which are sourced from jdk.java.net), as those are not published/supported by any
      // of the above projects."
      // (see https://hub.docker.com/_/openjdk)
      "openjdk:$jdk"
    } else if (isWindows) {
      when (jdk) {
        "8" -> "eclipse-temurin:8u472-b08-jdk-windowsservercore-ltsc2022@sha256:46d804b1c8a658fd84b8f3b3f39a1739b0f0ffccf41a682cea4847982de3bd08"
        "11" -> "eclipse-temurin:11.0.29_7-jdk-windowsservercore-ltsc2022@sha256:3b16568beff29ff623e7d72018cd6b08f4003964a342a907ad410a0b953f40e6"
        "17" -> "eclipse-temurin:17.0.16_8-jdk-windowsservercore-ltsc2022@sha256:a7dc8df0d1367405bf195cda6a12489b17c99691c2448ea97b9418915e24ca7e"
        "20" -> "eclipse-temurin:20.0.2_9-jdk-windowsservercore-ltsc2022@sha256:a36eee54cc400505a19157a0fb3000eebed7aba34efa2faa11aa80d9365a2e1e"
        "21" -> "eclipse-temurin:21.0.9_10-jdk-windowsservercore-ltsc2022@sha256:45a3d356d018942a497b877633f19db401828ecb2a1de3cda635b98d08bfbaeb"
        "25" -> "eclipse-temurin:25.0.1_8-jdk-windowsservercore-ltsc2022@sha256:556d727eb539fd9c6242e75d17e1a2bf59456ea8a37478cfbd6406ca6db0d2d1"
        else -> throw GradleException("Unexpected jdk version for Windows: $jdk")
      }
    } else {
      when (jdk) {
        "8" -> "eclipse-temurin:8u472-b08-jdk@sha256:b4e05de303ea02659ee17044d6b68caadfc462f1530f3a461482afee23379cdd"
        "11" -> "eclipse-temurin:11.0.29_7-jdk@sha256:189ce1c8831fa5bdd801127dad99f68a17615f81f4aa839b1a4aae693261929a"
        "17" -> "eclipse-temurin:17.0.16_8-jdk@sha256:06ee07a59dc7011f643baaa45889ecd15a0b9176490943b0e4379630e832ac2d"
        "20" -> "eclipse-temurin:20.0.2_9-jdk@sha256:a8010918241007417c8c0ce7d203cf110f8c945b56da01a13eb55af7eb3d3175"
        "21" -> "eclipse-temurin:21.0.9_10-jdk@sha256:81ad1240d91eeafe1ab4154e9ed2310b67cb966caad1d235232ae10abcb1fae2"
        "25" -> "eclipse-temurin:25.0.1_8-jdk@sha256:adc4533ea69967c783ac2327dac7ff548fcf6401a7e595e723b414c0a7920eb2"
        else -> throw GradleException("Unexpected jdk version for Linux: $jdk")
      }
    }
  } else if (vm == "openj9") {
    if (isWindows) {
      // ibm-semeru-runtimes doesn't publish windows images
      throw GradleException("Unexpected vm: $vm")
    } else {
      when (jdk) {
        "8" -> "ibm-semeru-runtimes:open-8u472-b08-jdk@sha256:63bb8aad02000edbc5f90222a018862f546a0ac21ec01d6b31af6202083297e8"
        "11" -> "ibm-semeru-runtimes:open-11.0.29_7-jdk@sha256:a0910e6646e71de764f56ea19238719cb150ffabb46c0f9d3323e4cb697d59dc"
        "17" -> "ibm-semeru-runtimes:open-17-jdk@sha256:ad9a76a79afef5f01b49d3a7487e017305cb76f7421cd88e9424ee1c96fe8c09"
        "20" -> "ibm-semeru-runtimes:open-20.0.2_9-jdk@sha256:e950be308506f63b61a196b55dc53059317dc32e0c25c5d863bfb4ef6911922a"
        "21" -> "ibm-semeru-runtimes:open-21.0.9_10-jdk@sha256:bd69dbe68315b72ebfa0d708511176c3317dd0c500dc462e7041570983f14c49"
        "25" -> "ibm-semeru-runtimes:open-25-jdk@sha256:58f8efd0e2b137c19e192a3d1a36e9efe070d6f59784bc4a84f551e6c148b35c"
        else -> throw GradleException("Unexpected jdk version for openj9: $jdk")
      }
    }
  } else {
    throw GradleException("Unexpected vm: $vm")
  }

  val extraArgs = args.toMutableMap()
  if (server == "wildfly") {
    // wildfly url without .zip or .tar.gz suffix
    val majorVersion = version.substring(0, version.indexOf(".")).toInt()
    val serverBaseUrl = if (majorVersion >= 25) {
      "https://github.com/wildfly/wildfly/releases/download/$version/wildfly-$version"
    } else {
      "https://download.jboss.org/wildfly/$version/wildfly-$version"
    }
    extraArgs["baseDownloadUrl"] = serverBaseUrl
  } else if (server == "payara") {
    if (version == "5.2020.6") {
      extraArgs["domainName"] = "production"
    } else {
      extraArgs["domainName"] = "domain1"
    }
  }

  val buildTask = tasks.register<DockerBuildImage>("${server}Image-$version-jdk$jdk$vmSuffix$platformSuffix") {
    dependsOn(prepareTask)
    group = "build"
    description = "Builds Docker image with $server $version on JDK $jdk-$vm${if (isWindows) " on Windows" else ""}"

    inputDir.set(dockerWorkingDir)
    images.add(image)
    dockerFile.set(File(dockerWorkingDir.get().asFile, dockerFileName))
    buildArgs.set(extraArgs + mapOf("jdk" to jdk, "vm" to vm, "version" to version, "jdkImage" to jdkImage))
    doLast {
      matrix.add(image)
    }
  }

  parentTask.configure {
    dependsOn(buildTask)
  }
  return image
}

fun createDockerTasks(parentTask: TaskProvider<out Task>, isWindows: Boolean) {
  val resultImages = mutableSetOf<String>()
  for ((server, matrices) in targets) {
    val smokeTestServer = findProperty("smokeTestServer")
    if (smokeTestServer != null && server != smokeTestServer) {
      continue
    }

    for (entry in matrices) {
      val dockerfile = server
      val extraArgs = entry.args
      val warProject = entry.war
      val supportsWindows = entry.windows

      for (version in entry.version) {
        for (vm in entry.vm) {
          if (vm == "openj9" && isWindows) {
            // ibm-semeru-runtimes doesn't publish windows images
            continue
          }
          for (jdk in entry.jdk) {
            if (supportsWindows || !isWindows) {
              resultImages.add(configureImage(parentTask, server, dockerfile, version, vm, jdk, warProject, extraArgs, isWindows))
            }
          }
        }
      }
    }
  }
}
