package com.epam.digital.data.platform.pipelines.stages.impl.buildJavaApplication

import com.epam.digital.data.platform.pipelines.stages.ProjectType
import com.epam.digital.data.platform.pipelines.stages.Stage

@Stage(
        name = "configure-openshift-build",
        buildTool = ["any"],
        type = [ProjectType.APPLICATION]
)
class ConfigureOpenShiftBuild {
    LocalBuildContext context

    void run() {
        context.script.deleteDir()

        String buildConfigFile = "${context.buildConfigName}-buildconfig.yaml"

        context.script.writeFile(
                file: buildConfigFile,
                text: """\
apiVersion: build.openshift.io/v1
kind: BuildConfig
metadata:
  name: ${context.buildConfigName}
  labels:
    build: ${context.buildConfigName}
spec:
  source:
    type: Binary
    binary: {}
  strategy:
    type: Docker
    dockerStrategy:
      buildArgs:
        - name: APPLICATION_VERSION_ARG
          value: \"${context.projectVersion}\"
  output:
    to:
      kind: DockerImage
      name: ${context.imageReference}
    pushSecret:
      name: ${context.dockerRegistry.PUSH_SECRET}
  runPolicy: Serial
  successfulBuildsHistoryLimit: 5
  failedBuildsHistoryLimit: 5
"""
        )

        context.platform.apply(buildConfigFile)

        context.logger.info("BuildConfig: ${context.buildConfigName}")
        context.logger.info("Output image: ${context.imageReference}")
    }
}
