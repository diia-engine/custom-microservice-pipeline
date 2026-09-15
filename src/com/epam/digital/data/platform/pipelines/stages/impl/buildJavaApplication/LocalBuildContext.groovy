package com.epam.digital.data.platform.pipelines.stages.impl.buildJavaApplication

import com.epam.digital.data.platform.pipelines.buildcontext.BuildContext

class LocalBuildContext extends BuildContext {
    final String jenkinsAgentLabel = 'dataplatform-jenkins-agent'

    String repositoryName
    String repositoryPath
    String gitBranch

    String javaVersion
    String mavenBuilderImage

    String projectArtifactId
    String projectVersion

    String buildConfigName
    String imageReference

    Map<String, String> helmValues

    LocalBuildContext(Script script) {
        super(script)
    }
}
