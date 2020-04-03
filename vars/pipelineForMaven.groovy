#!/usr/bin/env groovy
library(
    identifier: 'utils@master',
    retriever: modernSCM(
        [
            $class: 'GitSCMSource',
            remote: 'https://github.com/poshjosh/jenkins-shared-library.git'
        ]
    )
)
/**
 * <p>https://github.com/poshjosh</p>
 * Usage:
 * <code
 *     // Specify gitUrl, only if not specified in jenkins app
 *     pipelineForMavenDockerfile(gitUrl : 'link_to_your_git_repo_here')
 * </code>
 * <p>OR</p>
 * <code>
 *     pipelineForMavenDockerfile()
 * </code>
 */
def call(Map config=[:]) {
    pipeline {

        agent any

        parameters {
            string(name: 'ORG_NAME',
                    defaultValue: "${config.orgName ? config.orgName : utils.defaultConfig.orgName}",
                    description: 'Name of the organization. (Docker Hub/GitHub)')
            string(name: 'MAVEN_ARGS',
                    defaultValue: "${config.mavenArgs ? config.mavenArgs : utils.defaultConfig.mavenArgs}",
                    description: 'Maven arguments')
            string(name: 'TIMEOUT',
                    defaultValue: "${config.timeout ? config.timeout : utils.defaultConfig.timeout}",
                    description: 'Max time that could be spent in MINUTES')
            string(name: 'BUILD_CONTEXT',
                    defaultValue: "${config.buildContext ? config.buildContext : utils.defaultConfig.buildContext}",
                    description: 'Docker build context')
            string(name: 'FAILURE_EMAIL_RECIPIENT',
                    defaultValue: "${config.failureEmailRecipient ? config.failureEmailRecipient : utils.defaultConfig.failureEmailRecipient}",
                    description: 'The email address to send a message to on failure')
            choice(name: 'DEBUG', choices: ['N', 'Y'], description: 'Debug?')
        }

        environment {
            ARTIFACTID = readMavenPom().getArtifactId();
            VERSION = readMavenPom().getVersion()
            APP_ID = "${ARTIFACTID}:${VERSION}"
            IMAGE_REF = "${ORG_NAME}/${APP_ID}";
            IMAGE_NAME = IMAGE_REF.toLowerCase()
            MAVEN_ARGS = "${params.DEBUG == 'Y' ? '-X ' + params.MAVEN_ARGS : params.MAVEN_ARGS}"
            VOLUME_BINDINGS = '-v /home/.m2:/root/.m2'
        }

        options {
            timestamps()
            timeout(time: "${params.TIMEOUT}", unit: 'MINUTES')
            buildDiscarder(logRotator(numToKeepStr: '5'))
            skipStagesAfterUnstable()
            disableConcurrentBuilds()
        }

        triggers{
            // Once in every 4 hours slot between 0900 and 1700 every Monday - Friday
            pollSCM('H H(8-16)/4 * * 1-5')
        }

        stages {
            stage('Prepare') {
                steps {
                    echo " = = = = = = = PREPARING = = = = = = = "
                    script {

                        if(DEBUG == 'Y') {
                            echo '- - - - - - - Printing Environment - - - - - - -'
                            sh 'printenv'
                            echo '- - - - - - - Done Printing Environment - - - - - - -'
                        }

                        def dockerFileExists = sh(script : 'test -f Dockerfile', returnStatus : true)
                        if( ! dockerFileExists) {
                            utils.copyResourceToWorkspace(
                                srcFilename : 'Dockerfile_maven3alpine', destFilename : 'Dockerfile')
                        }

                        if(config.gitUrl) {

                            utils.checkoutGit "${config.gitUrl}"
                        }
                    }
                }
            }
            stage('Build Image') {
                steps {
                    echo " = = = = = = = BUILDING IMAGE = = = = = = = "
                    script {

                        def buildArgs
                        if(env.GIT_BRANCH == 'master') {
                            buildArgs = '--pull --no-cache'
                        }else{
                            buildArgs = '--pull'
                        }

                        echo "Building image: ${IMAGE_NAME} with build arguments: ${buildArgs}"
                        echo "env.GIT_BRANCH = ${env.GIT_BRANCH}"

                        docker.build("${IMAGE_NAME}", "${buildArgs} ${params.BUILD_CONTEXT}")
                    }
                }
            }
            stage('Clean & Install') {
                steps {
                    echo " = = = = = = = CLEAN & INSTALL = = = = = = = "
                    script{
                        docker.image("${IMAGE_NAME}").inside("${VOLUME_BINDINGS}"){
                            sh "mvn ${MAVEN_ARGS} clean:clean install:install"
                       }
                    }
                }
            }
            stage('Deploy Image') {
                when {
//                    branch 'master' // Only works for multibranch pipeline
                    expression {
                        return env.GIT_BRANCH == "master"
                    }
                }
                steps {
                    echo " = = = = = = = DEPLOYING IMAGE = = = = = = = "
                    script {
                        docker.withRegistry('', 'dockerhub-creds') { // Must have been specified in Jenkins
                            sh "docker push ${IMAGE_NAME}"
                        }
                    }
                }
            }
        }
        post {
            always {

                script{

                    utils.defaultRetry { deleteDir() }

                    utils.defaultRetry { sh "docker system prune -f --volumes" }
                }
            }
            failure {
                script{

                    utils.sendFailureEmail "${FAILURE_EMAIL_RECIPIENT}"
                }
            }
        }
    }
}
