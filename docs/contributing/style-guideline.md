## Style guideline

We follow the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html).

### Auto-formatting

The build will fail if the source code is not formatted according to the google java style.

The main goal is to avoid extensive reformatting caused by different IDEs having different opinion
about how things should be formatted by establishing.

Running

```bash
./gradlew spotlessApply
```

reformats all the files that need reformatting.

Running

```bash
./gradlew spotlessCheck
```

runs formatting verify task only.

#### Pre-commit hook

To completely delegate code style formatting to the machine,
there is a pre-commit hook setup to verify formatting before committing.
It can be activated with this command:

```bash
git config core.hooksPath .githooks
```

#### Editorconfig

As additional convenience for IntelliJ users, we provide `.editorconfig`
file. IntelliJ will automatically use it to adjust its code formatting settings.
It does not support all required rules, so you still have to run
`spotlessApply` from time to time.

### Additional checks

The build uses checkstyle to verify some parts of the Google Java Style Guide that cannot be handled
by auto-formatting.

To run these checks locally:

```
./gradlew checkstyleMain checkstyleTest
```

### Static imports

We leverage static imports for many common types of operations. However, not all static methods or
constants are necessarily good candidates for a static import. The following list is a very
rough guideline of what are commonly accepted static imports:

* Test assertions (JUnit and AssertJ)
* Mocking/stubbing in tests (with Mockito)
* Collections helpers (such as `singletonList()` and `Collectors.toList()`)
* ByteBuddy `ElementMatchers` (for building instrumentation modules)
* Immutable constants (where clearly named)
* Singleton instances (especially where clearly named an hopefully immutable)
* `tracer()` methods that expose tracer singleton instances
