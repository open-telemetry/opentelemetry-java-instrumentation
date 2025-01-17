val stableVersion = "2.12.0"
val alphaVersion = "2.12.0-alpha"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
