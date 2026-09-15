package com.epam.digital.data.platform.pipelines.stages.impl.buildJavaApplication

import com.epam.digital.data.platform.pipelines.stages.ProjectType
import com.epam.digital.data.platform.pipelines.stages.Stage

@Stage(
        name = "detect-build-configuration",
        buildTool = ["any"],
        type = [ProjectType.APPLICATION]
)
class DetectBuildConfiguration {

    LocalBuildContext context

    void run() {
        context.logger.info("Detect build configuration for ${context.repositoryName}")
        if (!context.script.fileExists('pom.xml')) {
            context.script.error('pom.xml not found')
        }

        def pom = context.script.readMavenPom(file: 'pom.xml')
        context.projectArtifactId = pom.artifactId.toString()
        context.projectVersion = pom.version.toString()

        context.logger.info("Project: ${context.projectArtifactId}")
        context.logger.info("Version: ${context.projectVersion}")

        context.buildConfigName = "${context.projectArtifactId}-${context.gitBranch}".toLowerCase().replaceAll('[^a-z0-9-]', '-')

        context.imageReference = "${context.dockerRegistry.host}/${context.namespace}/${context.projectArtifactId}:${context.projectVersion}"

        def properties = pom.properties
        context.javaVersion = properties['java.version'] ?: properties['maven.compiler.release'] ?: properties['maven.compiler.source']

        if (!context.javaVersion) {
            context.script.error('Unable to determine Java version from pom.xml')
        }

        context.javaVersion = context.javaVersion.toString()
        context.logger.info("Detected Java version: ${context.javaVersion}")

        switch (context.javaVersion) {
            case '8':
                context.mavenBuilderImage = 'maven:3.9-eclipse-temurin-8'
                break
            case '11':
                context.mavenBuilderImage = 'maven:3.9-eclipse-temurin-11'
                break
            case '17':
                context.mavenBuilderImage = 'maven:3.9-eclipse-temurin-17'
                break
            case '21':
                context.mavenBuilderImage = 'maven:3.9-eclipse-temurin-21'
                break
            default:
                context.script.error("Unsupported Java version: ${context.javaVersion}")
        }

        context.logger.info("Builder image: ${context.mavenBuilderImage}")
        context.logger.info("Image reference: ${context.imageReference}")
    }
}
