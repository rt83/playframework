package org.gradle.playframework.plugins;

import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.scala.internal.DefaultScalaPlatform;
import org.gradle.playframework.extensions.PlayExtension;
import org.gradle.playframework.extensions.PlayPlatform;
import org.gradle.playframework.tasks.JavaScriptMinify;
import org.gradle.plugins.ide.idea.GenerateIdeaModule;
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel;
import org.gradle.plugins.ide.idea.model.IdeaModule;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.gradle.api.plugins.JavaPlugin.CLASSES_TASK_NAME;
import static org.gradle.playframework.plugins.PlayApplicationPlugin.PLAY_EXTENSION_NAME;
import static org.gradle.playframework.plugins.internal.PlayPluginHelper.*;

public class PlayIdeaPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getTasks().named("ideaModule", GenerateIdeaModule.class, ideaModuleTask -> {
            IdeaModule module = ideaModuleTask.getModule();
            ConventionMapping conventionMapping = conventionMappingFor(module);

            TaskProvider<Task> classesTask = project.getTasks().named(CLASSES_TASK_NAME);
            TaskProvider<JavaScriptMinify> javaScriptMinifyTask = project.getTasks().named(PlayJavaScriptPlugin.JS_MINIFY_TASK_NAME, JavaScriptMinify.class);

            conventionMapping.map("sourceDirs", (Callable<Set<File>>) () -> {
                // TODO: Assets should probably be a source set too
                Set<File> sourceDirs = new HashSet<>();
                sourceDirs.add(new File(project.getProjectDir(), "public"));

                SourceDirectorySet scalaSourceDirectorySet = getMainScalaSourceDirectorySet(project);
                sourceDirs.addAll(scalaSourceDirectorySet.getSrcDirs());
                sourceDirs.add(javaScriptMinifyTask.get().getDestinationDir().get().getAsFile());
                return sourceDirs;
            });

            conventionMapping.map("singleEntryLibraries", (Callable<Map<String, Iterable<File>>>) () -> {
                SourceSet mainSourceSet = getMainJavaSourceSet(project);
                SourceSet testSourceSet = getTestJavaSourceSet(project);

                Map<String, Iterable<File>> libs = new HashMap<>();
                libs.put("COMPILE", mainSourceSet.getOutput().getClassesDirs());
                libs.put("RUNTIME", Collections.singleton(mainSourceSet.getOutput().getResourcesDir()));
                libs.put("TEST", testSourceSet.getOutput().getClassesDirs());
                return Collections.unmodifiableMap(libs);
            });

            PlayExtension playExtension = (PlayExtension) project.getExtensions().getByName(PLAY_EXTENSION_NAME);
            module.setScalaPlatform(new DefaultScalaPlatform(playExtension.getPlatform().getScalaVersion().get()));

            conventionMapping.map("targetBytecodeVersion", (Callable<JavaVersion>) () -> getTargetJavaVersion(playExtension.getPlatform()));
            conventionMapping.map("languageLevel", (Callable<IdeaLanguageLevel>) () -> new IdeaLanguageLevel(getTargetJavaVersion(playExtension.getPlatform())));

            ideaModuleTask.dependsOn(classesTask);
            ideaModuleTask.dependsOn(javaScriptMinifyTask);
        });
    }

    private ConventionMapping conventionMappingFor(IdeaModule module) {
        return new DslObject(module).getConventionMapping();
    }

    private JavaVersion getTargetJavaVersion(PlayPlatform Platform) {
        return Platform.getJavaVersion().get();
    }
}
