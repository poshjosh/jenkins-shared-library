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
 * <code>
 *     pipelineForMavenDockerfile(gitUrl : 'link_to_your_git_repo_here')
 * </code>
 */
def call(Map config=[:]) {
    pipeline {
        agent any
        parameters {
            string(name: 'ORG_NAME', defaultValue: 'poshjosh',
                    description: 'Name of the organization. (Docker Hub/GitHub)')
            string(name: 'MAVEN_ARGS', defaultValue: '-B',
                    description: 'Maven arguments')
            string(name: 'TIMEOUT', defaultValue: '30',
                    description: 'Max time that could be spent in MINUTES')
            string(name: 'FAILURE_EMAIL_RECIPIENT', defaultValue: '',
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
            timeout(time: "${config.timeout}", unit: "${config.timeoutUnit}")
            buildDiscarder(logRotator(numToKeepStr: '4'))
            skipStagesAfterUnstable()
            disableConcurrentBuilds()
        }

        triggers{
            // Once in every 2 hours slot between 0900 and 1600 every Monday - Friday
            pollSCM('H H(8-16)/2 * * 1-5')
        }

        stages {
            stage('Checkout SCM') {
                steps {
                    script {

                          if(DEBUG == 'Y') {
                              echo '- - - - - - - Printing Environment - - - - - - -'
                              sh 'printenv'
                              echo '- - - - - - - Done Printing Environment - - - - - - -'
                          }

                          utils.checkoutGit "${config.gitUrl}"
                    }
                }
            }
            stage('Build Image') {
                steps {
                    echo " = = = = = = = BUILDING IMAGE = = = = = = = "
                    script {
                        def additionalBuildArgs = "--pull"
                        if (env.BRANCH_NAME == "master") {
                            additionalBuildArgs = "--no-cache ${additionalBuildArgs}"
                        }
                        docker.build("${IMAGE_NAME}", "${additionalBuildArgs} .")
                    }
                }
            }
            stage('Clean & Install') {
                steps {
                    echo " = = = = = = = CLEAN & INSTALL = = = = = = = "
                    script{
                        docker.image("${IMAGE_NAME}").inside("${VOLUME_BINDINGS}"){
                            sh 'mvn ${MAVEN_ARGS} clean:clean install:install'
                       }
                    }
                }
            }
            stage('Deploy Image') {
                when {
//                    branch 'master' // Only works for multibranch pipeline
                    expression {
                        return env.GIT_BRANCH == "origin/master"
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

                    utils.cleanupWorkspace attempts : 3, timeout : 60, timeoutUnit : 'SECONDS'

                    utils.cleanupDocker attempts : 3, timeout : 60, timeoutUnit : 'SECONDS'
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
