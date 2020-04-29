## Contributing

Pull requests for bug fixes are welcome, but before submitting new features or changes to current functionality [open an issue](https://github.com/open-telemetry/opentelemetry-auto-instr-java/issues/new)
and discuss your ideas or propose the changes you wish to make. After a resolution is reached a PR can be submitted for review.

In order to fully build and test this whole repository you need the following:
* Installed both JDK 8 and 9.
* Java 8 should be set as default: `java -version` should give you version 8.
* Defined environment variables `JAVA_8_HOME` and `JAVA_9_HOME` which point to the corresponding java homes. 

### Testing
#### Java versions
Open Telemetry Auto Instrumentation's minimal supported version is java 7.
All jar files that we produce, unless noted otherwise, have bytecode compatible with java 7 runtime. 
In addition to that we test our code with all later java versions as well: from 8 to 14.

Some libraries that we auto-instrument may have higher minimal requirements.
In this case we compile and test corresponding auto-instrumentation with higher java version as required by library.
The resulting classes will have higher bytecode level, 
but as it matches library's java version, no runtime problem arise.

#### Instrumentation tests
Executing `./gradlew instrumentation:test` will run tests for all supported auto-instrumentations 
using that java version which runs the Gradle build itself. 
These tests usually use the minimal supported version of the instrumented library.

In addition to that each instrumentation has a separate test set called `latestDepTest`.
It was created by [Gradle test sets plugin](https://github.com/unbroken-dome/gradle-testsets-plugin).
It uses the very same tests as before, but declares a dynamic dependency on the latest available version of this library.
You can run them all by executing `./gradlew latestDepTest`.

#### Executing tests with specific java version
In order to run tests on a specific java version, just execute `./gradlew testJava7` (or `testJava11` or `latestDepTestJava14` etc).
Then Gradle task rule will kick in and do the following:
* check, if Gradle already runs on a java with required version
* if not, look for an environment variable named `JAVA_N_HOME`, where `N` is the requested java version
* if Gradle could not found requested java version, then build will fail
* Gradle will now find all corresponding test tasks and configure them to use java executable of the requested version.

This works both for tasks named `test` and `latestDepTest`.
But currently does not work for other custom test tasks, such as those created by test sets plugin.

### Code Style

This project includes a `.editorconfig` file for basic editor settings.  This file is supported by most common text editors.

Java files must be formatted using [google-java-format](https://github.com/google/google-java-format).  Please run the following task to ensure files are formatted before committing:

```shell 
./gradlew googleJavaFormat
```

Other source files (Groovy, Scala, etc) should ideally be formatted by Intellij Idea's default formatting, but are not enforced.

### Intellij IDEA

Required plugins:
* [Lombok](https://plugins.jetbrains.com/plugin/6317-lombok-plugin)

Suggested plugins and settings:

* Editor > Code Style > Java/Groovy > Imports
  * Class count to use import with '*': `9999` (some number sufficiently large that is unlikely to matter)
  * Names count to use static import with '*': `9999`
  * With java use the following import layout (groovy should still use the default) to ensure consistency with google-java-format:
    ![import layout](https://user-images.githubusercontent.com/734411/43430811-28442636-94ae-11e8-86f1-f270ddcba023.png)
* [Google Java Format](https://plugins.jetbrains.com/plugin/8527-google-java-format)
* [Save Actions](https://plugins.jetbrains.com/plugin/7642-save-actions)
  ![Recommended Settings](https://user-images.githubusercontent.com/734411/43430944-db84bf8a-94ae-11e8-8cec-0daa064937c4.png)
