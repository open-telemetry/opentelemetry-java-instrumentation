/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle.bytebuddy;

import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import net.bytebuddy.build.gradle.ByteBuddySimpleTask;
import net.bytebuddy.build.gradle.Transformation;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.AbstractCompile;

/**
 * Starting from version 1.10.15, ByteBuddy gradle plugin transformation task autoconfiguration is
 * hardcoded to be applied to javaCompile task. This causes the dependencies to be resolved during
 * an afterEvaluate that runs before any afterEvaluate specified in the build script, which in turn
 * makes it impossible to add dependencies in afterEvaluate. Additionally the autoconfiguration will
 * attempt to scan the entire project for tasks which depend on the compile task, to make each task
 * that depends on compile also depend on the transformation task. This is an extremely inefficient
 * operation in this project to the point of causing a stack overflow in some environments.
 *
 * <p>To avoid all the issues with autoconfiguration, this class manually configures the ByteBuddy
 * transformation task. This also allows it to be applied to source languages other than Java. The
 * transformation task is configured to run between the compile and the classes tasks, assuming no
 * other task depends directly on the compile task, but instead other tasks depend on classes task.
 * Contrary to how the ByteBuddy plugin worked in versions up to 1.10.14, this changes the compile
 * task output directory, as starting from 1.10.15, the plugin does not allow the source and target
 * directories to be the same. The transformation task then writes to the original output directory
 * of the compile task.
 */
public class ByteBuddyPluginConfigurator {
  private static final List<String> LANGUAGES = Arrays.asList("java", "scala", "kotlin");

  private final Project project;
  private final SourceSet sourceSet;
  private final String pluginClassName;
  private final Iterable<File> inputClasspath;

  public ByteBuddyPluginConfigurator(
      Project project, SourceSet sourceSet, String pluginClassName, Iterable<File> inputClasspath) {
    this.project = project;
    this.sourceSet = sourceSet;
    this.pluginClassName = pluginClassName;

    // add build resources dir to classpath if it's present
    File resourcesDir = sourceSet.getOutput().getResourcesDir();
    this.inputClasspath =
        resourcesDir == null
            ? inputClasspath
            : ImmutableList.<File>builder()
                .addAll(inputClasspath)
                .add(sourceSet.getOutput().getResourcesDir())
                .build();
  }

  public void configure() {
    String taskName = getTaskName();
    Task byteBuddyTask = project.getTasks().create(taskName);

    for (String language : LANGUAGES) {
      AbstractCompile compile = getCompileTask(language);

      if (compile != null) {
        Task languageTask = createLanguageTask(compile, taskName + language);
        // We also process resources for SPI classes.
        languageTask.dependsOn(sourceSet.getProcessResourcesTaskName());
        byteBuddyTask.dependsOn(languageTask);
      }
    }

    project.getTasks().getByName(sourceSet.getClassesTaskName()).dependsOn(byteBuddyTask);
  }

  private Task createLanguageTask(AbstractCompile compileTask, String name) {
    ByteBuddySimpleTask task = project.getTasks().create(name, ByteBuddySimpleTask.class);
    task.setGroup("Byte Buddy");
    task.getOutputs().cacheIf(unused -> true);

    File classesDirectory = compileTask.getDestinationDir();
    File rawClassesDirectory =
        new File(classesDirectory.getParent(), classesDirectory.getName() + "raw")
            .getAbsoluteFile();

    task.dependsOn(compileTask);
    compileTask.setDestinationDir(rawClassesDirectory);

    task.setSource(rawClassesDirectory);
    task.setTarget(classesDirectory);
    task.setClassPath(compileTask.getClasspath());

    task.dependsOn(compileTask);

    task.getTransformations().add(createTransformation(inputClasspath, pluginClassName));
    return task;
  }

  private AbstractCompile getCompileTask(String language) {
    Task task = project.getTasks().findByName(sourceSet.getCompileTaskName(language));

    if (task instanceof AbstractCompile) {
      AbstractCompile compile = (AbstractCompile) task;

      if (!compile.getSource().isEmpty()) {
        return compile;
      }
    }

    return null;
  }

  private String getTaskName() {
    if (SourceSet.MAIN_SOURCE_SET_NAME.equals(sourceSet.getName())) {
      return "byteBuddy";
    } else {
      return sourceSet.getName() + "ByteBuddy";
    }
  }

  private static Transformation createTransformation(
      Iterable<File> classPath, String pluginClassName) {
    Transformation transformation = new ClasspathTransformation(classPath, pluginClassName);
    transformation.setPlugin(ClasspathByteBuddyPlugin.class);
    return transformation;
  }
}
