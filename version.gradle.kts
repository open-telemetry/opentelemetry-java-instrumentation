val stableVersion = "1.33.5"
val alphaVersion = "1.33.5-alpha"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
