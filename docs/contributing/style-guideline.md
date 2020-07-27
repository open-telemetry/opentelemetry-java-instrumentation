### Style guideline

We follow the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html).
Our build will fail if source code is not formatted according to that style.

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
