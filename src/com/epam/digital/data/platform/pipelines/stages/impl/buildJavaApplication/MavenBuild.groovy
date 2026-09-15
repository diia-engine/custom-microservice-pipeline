package com.epam.digital.data.platform.pipelines.stages.impl.buildJavaApplication

import com.epam.digital.data.platform.pipelines.stages.ProjectType
import com.epam.digital.data.platform.pipelines.stages.Stage

@Stage(
        name = "maven-build",
        buildTool = ["any"],
        type = [ProjectType.APPLICATION]
)
class MavenBuild {

    LocalBuildContext context

    void run() {
        context.logger.info(
                "Starting Maven build with image: ${context.mavenBuilderImage}"
        )

        context.script.podTemplate(
                containers: [
                        context.script.containerTemplate(
                                name: 'maven',
                                image: context.mavenBuilderImage,
                                command: 'cat',
                                ttyEnabled: true
                        )
                ]
        ) {
            context.script.node(context.script.POD_LABEL) {
                context.script.container('maven') {
                    context.script.unstash('source')

                    context.script.sh('''#!/usr/bin/env bash
                        # Jenkins executes commands through an exec session, whose
                        # output is not written to the container log. Duplicate it
                        # to PID 1 stdout so it is also available via `oc logs`.
                        exec > >(tee /proc/1/fd/1) 2>&1

                        echo "=== JAVA VERSION ==="
                        java -version

                        echo "=== MAVEN VERSION ==="
                        mvn -version

                        echo "=== BUILD ==="
                        mvn clean package

                        echo "=== ARTIFACTS ==="
                        find target -maxdepth 1 -type f -name '*.jar' -ls
                    ''')

                    context.script.stash(
                            name: 'artifact',
                            includes: 'target/*.jar',
                            excludes: 'target/*.jar.original',
                            useDefaultExcludes: false
                    )
                    context.script.stash(
                            name: 'docker-context',
                            includes: 'Dockerfile,target/*.jar',
                            excludes: 'target/*.jar.original',
                            useDefaultExcludes: false
                    )
                }
            }
        }
    }
}
