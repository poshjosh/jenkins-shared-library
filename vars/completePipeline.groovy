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
 *     completePipeline(
 *         appPort : '9010',                      // optional
 *         appEndpoint : '/actuator/health',      // optional
 *         mainClass : 'com.abc.Main',            // optional
 *         gitUrl : 'link_to_your_git_repo_here') // Only if not specified in jenkins app
 * </code>
 */
def call(Map config=[:]) {
    pipeline {

        agent any

        /**
         * At a minimum, provide the MAIN_CLASS and where applicable APP_PORT
         * You may also need to specify SONAR_BASE_URL if not <tt>localhost</tt>
         */
        parameters {
            string(name: 'ORG_NAME',
                    defaultValue: "${config.orgName ? config.orgName : utils.defaultConfig.orgName}",
                    description: 'Name of the organization. (Docker Hub/GitHub)')
            string(name: 'MAVEN_ARGS',
                    defaultValue: "${config.mavenArgs ? config.mavenArgs : utils.defaultConfig.mavenArgs}",
                    description: 'Maven arguments')
            string(name: 'APP_BASE_URL',
                    defaultValue: "${config.appBaseUrl ? config.appBaseUrl : utils.defaultConfig.baseUrl}",
                    description: 'Server  protocol://host, without the port')
            string(name: 'APP_PORT', defaultValue: "${config.appPort ? config.appPort : ''}",
                    description: 'App server port')
            string(name: 'APP_ENDPOINT', defaultValue: "${config.appEndpoint ? config.appEndpoint : ''}",
                    description: 'Must begin with a forward slash /. Endpoint to append to app host for HTTP requests.')
            string(name: 'JAVA_OPTS',
                    defaultValue: "${config.javaOpts ? config.javaOpts : utils.defaultConfig.javaOpts}",
                    description: 'Java environment variables')
            string(name: 'CMD_LINE_ARGS', defaultValue: "${config.cmdLineArgs ? config.cmdLineArgs : ''}",
                    description: 'Command line arguments')
            string(name: 'MAIN_CLASS', defaultValue: "${config.mainClass ? config.mainClass : ''}",
                    description: 'Java main class')
            string(name: 'SONAR_BASE_URL',
                    defaultValue: "${config.sonarBaseUrl ? config.sonarBaseUrl : utils.defaultConfig.baseUrl}",
                    description: '<base_url>:<port> = sonar.host.url')
            string(name: 'SONAR_PORT',
                    defaultValue: "${config.sonarPort ? config.sonarPort : utils.defaultConfig.sonarPort}",
                    description: 'Port for Sonarqube server')
            string(name: 'TIMEOUT', defaultValue: "${config.timeout ? config.timeout : utils.defaultConfig.timeout}",
                    description: 'Max time that could be spent in MINUTES')
            string(name: 'FAILURE_EMAIL_RECIPIENT',
                    defaultValue: "${config.failureEmailRecipient ? config.failureEmailRecipient : utils.defaultConfig.failureEmailRecipient}",
                    description: 'The email address to send a message to on failure')
            choice(name: 'DEBUG', choices: ['N', 'Y'], description: 'Debug?')
        }
        environment {
            ARTIFACTID = readMavenPom().getArtifactId()
            VERSION = readMavenPom().getVersion()
            APP_ID = "${ARTIFACTID}:${VERSION}"
            IMAGE_REF = "${ORG_NAME}/${APP_ID}"
            IMAGE_NAME = IMAGE_REF.toLowerCase()
            MAVEN_WORKSPACE = ''
            MAVEN_ARGS = "${params.DEBUG == 'Y' ? '-X ' + params.MAVEN_ARGS : params.MAVEN_ARGS}"
            SERVER_URL = "${(params.APP_BASE_URL && params.APP_PORT) ? (params.APP_BASE_URL + ':' + params.APP_PORT + params.APP_ENDPOINT) : ''}"
            SONAR_URL = "${(params.SONAR_BASE_URL && params.SONAR_PORT) ? (params.SONAR_BASE_URL + ':' + params.SONAR_PORT) : ''}"
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
            stage('Checkout SCM') {
                when {
                    expression {
                        return (config.gitUrl != null && config.gitUrl != '')
                    }
                }
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
            stage('Maven') {
                agent {
                    docker {
                        image 'maven:3-alpine'
                        args "--name maven-3-alpine -u root ${VOLUME_BINDINGS}"
                    }
                }
                stages {
                    stage('Test & Package') {
                        steps {
                            echo '- - - - - - - TEST & PACKAGE - - - - - - -'
                            script {

                                MAVEN_WORKSPACE = WORKSPACE

                                if(DEBUG == 'Y') {
                                    echo '- - - - - - - Printing Environment - - - - - - -'
                                    sh 'printenv'
                                    echo '- - - - - - - Done Printing Environment - - - - - - -'
                                }
                            }
                            sh "mvn ${MAVEN_ARGS} clean package"
                            jacoco execPattern: 'target/jacoco.exec'
                        }
                        post {
                            always {
                                archiveArtifacts artifacts: 'target/*.jar', onlyIfSuccessful: true
                                junit(
                                    allowEmptyResults: true,
                                    testResults: 'target/surefire-reports/*.xml'
                                )
                            }
                        }
                    }
                    stage('Quality Assurance') {
                        // parallel {
                        stages {
                            stage('Integration Tests') {
                                steps {
                                    echo '- - - - - - - INTEGRATION TESTS - - - - - - -'
                                    sh "mvn ${MAVEN_ARGS} failsafe:integration-test failsafe:verify"
                                    jacoco execPattern: 'target/jacoco-it.exec'
                                }
                                post {
                                    always {
                                        junit(
                                            allowEmptyResults: true,
                                            testResults: 'target/failsafe-reports/*.xml'
                                        )
                                    }
                                }
                            }
                            stage('Sanity Check') {
                                steps {
                                    echo '- - - - - - - SANITY CHECK - - - - - - -'
                                    // On error, fail the stage, but continue pipeline as success
                                    catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                                        sh "mvn ${MAVEN_ARGS} checkstyle:checkstyle pmd:pmd pmd:cpd com.github.spotbugs:spotbugs-maven-plugin:spotbugs"
                                    }
                                }
                            }
                            stage('Static Code Analysis') {
                                when {
                                    expression {
                                        return (env.SONAR_URL != null && env.SONAR_URL != '')
                                    }
                                }
                                environment {
                                    SONAR = credentials('sonar-creds') // Must have been specified in Jenkins
                                }
                                steps {
                                    echo '- - - - - - - SONAR SCAN - - - - - - -'
                                    script{
                                        sh "mvn ${MAVEN_ARGS} sonar:sonar -Dsonar.login=${SONAR_USR} -Dsonar.password=${SONAR_PSW} -Dsonar.host.url=${env.SONAR_URL}"
                                    }
                                }
                            }
                            stage('Documentation') {
                                steps {
                                    echo '- - - - - - - DOCUMENTATION - - - - - - -'
                                    sh "mvn ${MAVEN_ARGS} site:site"
                                }
                                post {
                                    always {
                                        publishHTML(target: [reportName: 'Site', reportDir: 'target/site', reportFiles: 'index.html', keepAll: false])
                                    }
                                }
                            }
                        }
                    }
                    stage('Install Local') {
                        steps {
                            echo '- - - - - - - INSTALL LOCAL - - - - - - -'
                            sh "mvn ${MAVEN_ARGS} source:jar install:install"
                        }
                    }
                }
            }
            stage('Docker') {
                when {
                    expression {
                        return (params.MAIN_CLASS != null && params.MAIN_CLASS != '')
                    }
                }
                stages{
                    stage('Build Image') {
                        steps {
                            echo '- - - - - - - BUILD IMAGE - - - - - - -'
                            script {
                                // a dir target should exist if we have packaged our app e.g via mvn package or mvn jar:jar'

                                echo "Copying workspace containing maven artifact: ${MAVEN_WORKSPACE}/target"
                                sh "cp -r ${MAVEN_WORKSPACE}/target target"

                                echo "Copying dependencies"
                                sh "cd target && mkdir dependency && cd dependency && find ${WORKSPACE}/target -type f -name '*.jar' -exec jar -xf {} ';'"

                                def customArgs = "--build-arg MAIN_CLASS=${params.MAIN_CLASS} --build-arg JAVA_OPTS=${params.JAVA_OPTS}"
                                def additionalBuildArgs = "--pull ${customArgs}"

                                echo "Building image: ${IMAGE_NAME}"
                                docker.build("${IMAGE_NAME}", "${additionalBuildArgs} .")
                            }
                        }
                    }
                    stage('Run Image') {
                        steps {
                            echo '- - - - - - - RUN IMAGE - - - - - - -'
                            script{

                                def CONTAINER_NAME = ARTIFACTID
                                def RUN_ARGS = ARTIFACTID + ' ' + VOLUME_BINDINGS
                                if(params.APP_PORT) {
                                    RUN_ARGS = "${RUN_ARGS} -p ${params.APP_PORT}:${params.APP_PORT}"
                                }
                                if(params.JAVA_OPTS) {
                                    RUN_ARGS = "${RUN_ARGS} -e JAVA_OPTS=${JAVA_OPTS}"
                                }

                                // Add server port to command line args
                                def CMD_LINE
                                if(env.SERVER_URL) {
                                    CMD_LINE = params.CMD_LINE_ARGS + ' --server-port=' + params.APP_PORT
                                }else{
                                    CMD_LINE = params.CMD_LINE_ARGS
                                }

                                echo "RUN_ARGS = ${RUN_ARGS}"
                                echo "CMD_LINE = ${CMD_LINE}"

                                docker.image("${IMAGE_NAME}")
                                    .withRun("${RUN_ARGS}", "${CMD_LINE}") { c ->

                                        def hostIp = utils.getHostIpForContainerId "${c.id}"
                                        echo "Host ip = ${hostIp}"

                                        // SERVER_URL is an environment variable not a pipeline parameter
                                        if(env.SERVER_URL) {
                                            sleep 10
                                            sh "curl --retry 3 --retry-connrefused --connect-timeout 30 --max-time 60 ${SERVER_URL}"
                                        }else {
                                            echo "No Server URL"
                                        }
                                }
                            }
                        }
                    }
                    stage('Deploy Image') {
                        when {
//                            branch 'master' // Only works for multibranch pipeline
                            expression {
                                return env.GIT_BRANCH == "origin/master"
                            }
                        }
                        steps {
                            echo '- - - - - - - DEPLOY IMAGE - - - - - - -'
                            script {
                                // Must have been specified in Jenkins
                                docker.withRegistry('', 'dockerhub-creds') {
                                    sh "docker push ${IMAGE_NAME}"
                                }
                            }
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
