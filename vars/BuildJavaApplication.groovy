import com.epam.digital.data.platform.pipelines.platform.PlatformFactory
import com.epam.digital.data.platform.pipelines.registrycomponents.external.DockerRegistry
import com.epam.digital.data.platform.pipelines.registrycomponents.external.Keycloak
import com.epam.digital.data.platform.pipelines.registrycomponents.regular.Gerrit
import com.epam.digital.data.platform.pipelines.stages.StageFactory
import com.epam.digital.data.platform.pipelines.tools.GitClient
import com.epam.digital.data.platform.pipelines.tools.Logger
import com.epam.digital.data.platform.pipelines.stages.impl.buildJavaApplication.LocalBuildContext
import com.epam.digital.data.platform.pipelines.codebase.Codebase
import com.epam.digital.data.platform.pipelines.stages.ProjectType
import groovy.json.JsonSlurperClassic

void call() {
    LocalBuildContext context = new LocalBuildContext(this)

    node("master") {
        stage("Init") {
            String logLevel = context.getLogLevel()
            context.logger = new Logger(context.script)
            context.logger.init(logLevel)

            context.platform = new PlatformFactory(context).getPlatformImpl()
            context.namespace = context.getParameterValue("CI_NAMESPACE")
            context.dnsWildcard = context.platform.getJsonPathValue("jenkins", "jenkins", ".spec.edpSpec.dnsWildcard")

            context.dockerRegistry = new DockerRegistry(context)
            context.dockerRegistry.init()

            context.keycloak = new Keycloak(context)
            context.keycloak.init()

            context.codebase = new Codebase(context)
            context.codebase.buildToolSpec = "any"
            context.codebase.type = ProjectType.APPLICATION.getValue()

            context.stageFactory = new StageFactory(context)
            context.stageFactory.init()

            context.gitServer = new Gerrit(context, "gerrit")
            context.gitServer.init()

            context.gitClient = new GitClient(context)

            context.repositoryName = context.getParameterValue('REPOSITORY_NAME')
            context.gitBranch = context.getParameterValue('GIT_BRANCH')
            context.repositoryPath = "ssh://${context.gitServer.autouser}@${context.gitServer.host}:${context.gitServer.sshPort}/${context.repositoryName}"

            def customHelmValues = context.getParameterValue('HELM_VALUES', '{}')
            context.helmValues = new JsonSlurperClassic().parseText(customHelmValues) as Map

            context.logger.debug('====== Init stage finished. Configuration: ======')
            context.logger.debug("namespace: ${context.namespace}")
            context.logger.debug("dnsWildcard: ${context.dnsWildcard}")
            context.logger.debug("repositoryName: ${context.repositoryName}")
            context.logger.debug("gitBranch: ${context.gitBranch}")
            context.logger.debug("Gitserver config: ${context.gitServer.toString()}")
            context.logger.debug("repositoryPath: ${context.repositoryPath}")
            context.logger.debug("Keycloak: ${context.keycloak}")
            context.logger.debug("Custom helm values: ${context.helmValues}")
        }

    }

    node(context.jenkinsAgentLabel) {
        [
                'checkout',
                'detect-build-configuration',
                'maven-build',
                'configure-openShift-build',
                'build-image',
                'deploy',
        ].each { stage ->
            context.stageFactory.runStage(stage, context)
        }
    }
}
