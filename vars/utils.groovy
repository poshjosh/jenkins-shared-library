#!/usr/bin/env groovy

@groovy.transform.Field
def defaultConfig = [
    orgName : 'poshjosh',
    mavenArgs : '-B',
    baseUrl : 'http://3.19.158.114',
    sonarPort : '9000',
    timeout : '30', // minutes
    buildContext : '.',
    failureEmailRecipient : 'posh.bc@gmail.com']

/**
 * Usage:
 * <code>
 *     checkoutGit(gitUrl : 'link_to_your_git_repo_here')
 * </code>
 */
def checkoutGit(String gitUrl) {
    echo "Git URL: ${gitUrl}"
    checkout([$class: 'GitSCM',
        branches: [[name: '**']],
        doGenerateSubmoduleConfigurations: false,
        extensions: [],
        submoduleCfg: [],
        userRemoteConfigs: [[url: "${gitUrl}"]]
    ])
}

/**
 * Usage:
 * <code>
 *     sendFailureEmail(failureEmailRecipient : 'put_recipient_email_address_here')
 * </code>
 */
def sendFailureEmail(String failureEmailRecipient) {
    if(failureEmailRecipient) {
        mail(
            to: "${failureEmailRecipient}",
            subject: "$IMAGE_NAME - Build # $BUILD_NUMBER - FAILED!",
            body: "$IMAGE_NAME - Build # $BUILD_NUMBER - FAILED:\n\nCheck console output at ${env.BUILD_URL} to view the results."
        )
    }else{
        echo 'Failure email recipient not specified. Email will not be sent.'
    }
}

/**
 * Usage:
 * <code>
 *     def doIt{
 *         echo 'Doing it'
 *     }
 *     defaultRetry(this&doIt)
 * </code>
 */
def defaultRetry(Closure body) {
    retry(3) {
        try {
            timeout(time: 60, unit: 'SECONDS') {
                body()
            }
        } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
            // we re-throw as a different error, that would not
            // cause retry() to fail (workaround for issue JENKINS-51454)
            error 'Timeout!'
        }
    } // retry ends
}
