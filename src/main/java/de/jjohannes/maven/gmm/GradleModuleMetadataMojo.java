/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.jjohannes.maven.gmm;

import org.apache.maven.Maven;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;

/**
 * Goal that generates Gradle Module Metadata.
 */
@Mojo(name = "gmm", defaultPhase = LifecyclePhase.PACKAGE)
public class GradleModuleMetadataMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    @Parameter
    protected List<Dependency> platformDependencies;

    @Parameter
    protected List<Capability> capabilities;

    @Parameter
    protected List<Dependency> removedDependencies;

    @Parameter(defaultValue = "${project.build.directory}/publications/maven")
    private File outputDirectory;

    public void execute() throws MojoExecutionException {
        if ("pom".equals(project.getPackaging())) {
            // publishing GMM for platforms is currently not supported, the BOM can be used as platform directly
            return;
        }
        assertMarkerPropertyDefinedInPom();
        if (!outputDirectory.exists()) {
            //noinspection ResultOfMethodCallIgnored
            outputDirectory.mkdirs();
        }

        File moduleFile = new File(outputDirectory, "module.json");

        try (FileWriter fileWriter = new FileWriter(moduleFile)) {
            GradleModuleMetadataWriter.generateTo(
                    project, getMavenVersion(),
                    platformDependencies, capabilities,
                    removedDependencies,
                    fileWriter);
        } catch (IOException e) {
            throw new MojoExecutionException("Error creating file " + moduleFile, e);
        }

        Artifact gmmArtifact = new DefaultArtifact(project.getGroupId(), project.getArtifactId(), project.getVersion(),
                null, "module", null, new DefaultArtifactHandler("module"));
        gmmArtifact.setFile(moduleFile);
        project.addAttachedArtifact(gmmArtifact);
    }
    

    private void assertMarkerPropertyDefinedInPom() {
        File pomFile = project.getFile();
        if (!project.getProperties().containsKey("published-with-gradle-metadata")) {
            throw new RuntimeException("Please add the Gradle Module Metadata properties marker 'published-with-gradle-metadata' to " + pomFile.getAbsolutePath());
        }
    }

    private static String getMavenVersion() throws MojoExecutionException {
        try (InputStream resource = Maven.class.getClassLoader().getResourceAsStream("org/apache/maven/messages/build.properties")) {
            if (resource == null) {
                throw new MojoExecutionException("Unable to determine Maven version.");
            }
            Properties properties = new Properties();
            properties.load(resource);
            String version = properties.getProperty("version");
            if (version == null) {
                throw new MojoExecutionException("Unable to determine Maven version.");
            }
            return version;
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to determine Maven version.", e);
        }
    }
}
