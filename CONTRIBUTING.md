### Contributing

Pull requests for bug fixes are welcome, but before submitting new features or changes to current functionality [open an issue](https://github.com/DataDog/dd-trace-java/issues/new)
and discuss your ideas or propose the changes you wish to make. After a resolution is reached a PR can be submitted for review.

### Code Style

This project includes a `.editorconfig` file for basic editor settings.  This file is supported by most common text editors.

Java files must be formatted using [google-java-format](https://github.com/google/google-java-format).  Please run the following task to ensure files are formatted before committing:

```shell 
./gradlew googleJavaFormat
```

Other source files (Groovy, Scala, etc) should ideally be formatted by Intellij Idea's default formatting, but are not enforced.

### Intellij Idea

Suggested plugins and settings:

* Editor > Code Style > Java/Groovy > Imports
  * Class count to use import with '*': `50` (some number sufficiently large that is unlikely to matter)
  * Names count to use static import with '*': `50`
  * With java use the following import layout (groovy should still use the default) to ensure consistency with google-java-format:
    ![import layout](https://user-images.githubusercontent.com/734411/43430811-28442636-94ae-11e8-86f1-f270ddcba023.png)
* [Google Java Format](https://plugins.jetbrains.com/plugin/8527-google-java-format)
* [Lombok](https://plugins.jetbrains.com/plugin/6317-lombok-plugin)
* [Save Actions](https://plugins.jetbrains.com/plugin/7642-save-actions)
  ![Recommended Settings](https://user-images.githubusercontent.com/734411/43430944-db84bf8a-94ae-11e8-8cec-0daa064937c4.png)
