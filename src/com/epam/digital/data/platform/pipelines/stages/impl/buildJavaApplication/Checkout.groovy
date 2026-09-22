package com.epam.digital.data.platform.pipelines.stages.impl.buildJavaApplication

import com.epam.digital.data.platform.pipelines.stages.ProjectType
import com.epam.digital.data.platform.pipelines.stages.Stage

@Stage(
        name = "checkout-repository",
        buildTool = ["any"],
        type = [ProjectType.APPLICATION]
)
class Checkout {

    LocalBuildContext context

    void run() {
        context.logger.info("Checkout repository ${context.repositoryName}")

        context.script.deleteDir()
        context.gitClient.checkout(context.repositoryPath, context.gitBranch, context.gitServer.credentialsId)
        context.script.stash(
                name: 'source',
                includes: '**',
                excludes: '.git/**',
                useDefaultExcludes: false
        )
    }
}
