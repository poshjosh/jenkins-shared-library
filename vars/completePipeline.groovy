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
 *         mainClass : 'com.my.MainClass'         // No docker stage without this
 *         gitUrl : 'link_to_your_git_repo_here'  // Only if not specified in jenkins app
 *    )
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
            string(name: 'APP_BASE_URL',
                    defaultValue: "${config.appBaseUrl ? config.appBaseUrl : utils.defaultConfig.baseUrl}",
                    description: 'Server  protocol://host, without the port')
            string(name: 'APP_PORT', defaultValue: "${config.appPort ? config.appPort : ''}",
                    description: 'App server port')
            string(name: 'APP_ENDPOINT', defaultValue: "${config.appEndpoint ? config.appEndpoint : ''}",
                    description: 'Must begin with a forward slash /. Endpoint to append to app host for HTTP requests.')
            string(name: 'JAVA_OPTS',
                    defaultValue: "${config.javaOpts ? config.javaOpts : ''}",
                    description: 'Java environment variables')
            string(name: 'CMD_LINE_ARGS', defaultValue: "${config.cmdLineArgs ? config.cmdLineArgs : ''}",
                    description: 'Command line arguments')
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
                        return (config.mainClass != null && config.mainClass != '')
                    }
                }
                stages{
                    stage('Build Image') {
                        steps {
                            echo '- - - - - - - BUILD IMAGE - - - - - - -'
                            script {

                                // a dir target should exist if we have packaged our app via mvn package or mvn jar:jar'

                                // The way the shell commands are seperated is important
                                // Each shell is separate, does not continue from previous
                                //
                                echo "Copying workspace containing maven artifact: ${MAVEN_WORKSPACE}/target"
                                sh "cp -r ${MAVEN_WORKSPACE}/target target"

                                echo "Copying dependencies"
                                sh "cd target && mkdir dependency && cd dependency && find ${WORKSPACE}/target -type f -name '*.jar' -exec jar -xf {} ';'"

                                def buildArgs
                                if(env.GIT_BRANCH == 'master') {
                                    buildArgs = '--pull --no-cache --build-arg MAIN_CLASS=' + config.mainClass
                                }else{
                                    buildArgs = '--pull --build-arg MAIN_CLASS=' + config.mainClass
                                }
                                def javaOpts
                                if(params.APP_PORT) {
                                    if(params.JAVA_OPTS) {
                                        javaOpts = params.JAVA_OPTS + ' -DSERVER_PORT=' + params.APP_PORT
                                    }else{
                                        javaOpts = '-DSERVER_PORT=' + params.APP_PORT
                                    }
                                }else{
                                    javaOpts = params.JAVA_OPTS
                                }
                                buildArgs = buildArgs + " --build-arg JAVA_OPTS='" + javaOpts + "'"
                                if(params.DEBUG == 'Y') {
                                    buildArgs = buildArgs + ' --build-arg DEBUG=true'
                                }

                                echo "Building image: ${IMAGE_NAME} with build arguments: ${buildArgs}"

                                docker.build("${IMAGE_NAME}", "${buildArgs} .")
                            }
                        }
                    }
                    stage('Run Image') {
                        steps {
                            echo '- - - - - - - RUN IMAGE - - - - - - -'
                            script{

                                def c = ARTIFACTID + '-' + VERSION
                                def CONTAINER_NAME = c.toLowerCase()
                                def RUN_ARGS = '--name ' + CONTAINER_NAME + ' -u 0 ' + VOLUME_BINDINGS
                                if(params.APP_PORT) {
                                    RUN_ARGS = "${RUN_ARGS} -p ${params.APP_PORT}:${params.APP_PORT}"
                                }

                                echo "RUN_ARGS = ${RUN_ARGS}"
                                echo "env.GIT_BRANCH = ${env.GIT_BRANCH}"

                                docker.image("${IMAGE_NAME}")
                                    .withRun("${RUN_ARGS}", "${params.CMD_LINE_ARGS}") {

                                        sleep 10

                                        if(params.DEBUG == 'Y') {
                                            sh "docker logs ${CONTAINER_NAME}"
                                        }

                                        // SERVER_URL is an environment variable not a pipeline parameter
                                        if(env.SERVER_URL) {
                                            sh "curl --retry 3 --retry-connrefused --connect-timeout 10 --max-time 60 ${SERVER_URL}"
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
                                return env.GIT_BRANCH == "master"
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
