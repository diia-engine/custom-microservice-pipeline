package com.epam.digital.data.platform.pipelines.stages.impl.buildJavaApplication

import com.epam.digital.data.platform.pipelines.stages.ProjectType
import com.epam.digital.data.platform.pipelines.stages.Stage

@Stage(
        name = "build-image",
        buildTool = ["any"],
        type = [ProjectType.APPLICATION]
)
class BuildImage {
    LocalBuildContext context

    void run() {
        context.script.deleteDir()
        context.script.unstash('docker-context')

        context.logger.info("Starting OpenShift build ${context.buildConfigName} for image ${context.imageReference}")

        context.script.sh(script: """
            echo "=== BUILD CONTEXT ==="
            find . -maxdepth 3 -type f | sort

            echo "=== START OPENSHIFT BUILD ==="
            oc start-build ${context.buildConfigName} \\
                --from-dir=. \\
                --follow

            echo "=== BUILD RESULT ==="
            oc get builds \\
                -l buildconfig=${context.buildConfigName} \\
                --sort-by=.metadata.creationTimestamp
        """)
    }
}
