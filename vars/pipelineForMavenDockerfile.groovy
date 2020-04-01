#!/usr/bin/env groovy
@Library('options') _
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

        options(timeout : "${params.TIMEOUT}", timeoutUnit : 'MINUTES', numberOfBuildsToKeep : 5)

        triggers {
            // @TODO use webhooks from GitHub
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
                          echo "Git URL: ${config.gitUrl}"
                          checkout([$class: 'GitSCM',
                              branches: [[name: '**']],
                              doGenerateSubmoduleConfigurations: false,
                              extensions: [],
                              submoduleCfg: [],
                              userRemoteConfigs: [[url: "${config.gitUrl}"]]
                          ])
                    }
                    echo 'Printing host ip'
                    sh "$(ip -4 addr show docker0 | grep -Po 'inet \K[\d.]+')"
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
                    retry(3) {
                        try {
                            timeout(time: 60, unit: 'SECONDS') {
                                deleteDir() // Clean up workspace
                            }
                        } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
                            // we re-throw as a different error, that would not
                            // cause retry() to fail (workaround for issue JENKINS-51454)
                            error 'Timeout!'
                        }
                    } // retry ends
                    retry(3) {
                        try {
                            timeout(time: 60, unit: 'SECONDS') {
                                sh "docker system prune -f --volumes"
                            }
                        } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
                            // we re-throw as a different error, that would not
                            // cause retry() to fail (workaround for issue JENKINS-51454)
                            error 'Timeout!'
                        }
                    } // retry ends
                }
            }
            failure {
                script{
                    if(FAILURE_EMAIL_RECIPIENT != null && FAILURE_EMAIL_RECIPIENT != '') {
                        mail(
                            to: "${FAILURE_EMAIL_RECIPIENT}",
                            subject: "$IMAGE_NAME - Build # $BUILD_NUMBER - FAILED!",
                            body: "$IMAGE_NAME - Build # $BUILD_NUMBER - FAILED:\n\nCheck console output at ${env.BUILD_URL} to view the results."
                        )
                    }
                }
            }
        }
    }
}
