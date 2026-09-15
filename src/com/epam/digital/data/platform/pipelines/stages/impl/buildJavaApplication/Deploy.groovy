package com.epam.digital.data.platform.pipelines.stages.impl.buildJavaApplication

import com.epam.digital.data.platform.pipelines.stages.ProjectType
import com.epam.digital.data.platform.pipelines.stages.Stage
import com.epam.digital.data.platform.pipelines.tools.Helm

@Stage(
        name = "deploy",
        buildTool = ["any"],
        type = [ProjectType.APPLICATION]
)
class Deploy {
    LocalBuildContext context

    void run() {
        context.script.deleteDir()
        context.script.unstash('source')

        String imageName = "${context.dockerRegistry.host}/${context.namespace}/${context.projectArtifactId}"

        context.logger.info("Deploying ${context.projectArtifactId}:${context.projectVersion}")

        Map parametersMap = [
                'namespace': context.namespace,
                'cdPipelineStageName': 'main',
                'dnsWildcard'        : context.dnsWildcard,
                'image.name'         : imageName,
                'image.version'      : context.projectVersion,
                'nexusPullSecret'    : context.dockerRegistry.PUSH_SECRET,
                'keycloak.url'       : context.keycloak.url + "/auth",
        ]
        parametersMap.putAll(context.helmValues)

        Helm.upgrade(context, context.projectArtifactId, 'deploy-templates', parametersMap, '', context.namespace, true)
    }
}
