#!/usr/bin/env groovy

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
 *     cleanupDocker(attempts : 3, timeout : 60, timeoutUnit : 'SECONDS')
 * </code>
 * <p>OR</p>
 * <code>
 *     cleanupDocker()
 * </code>
 */
def cleanupDocker(int attempts, int timeout = 30, String timeoutUnit = 'SECONDS') {
    retry("${attempts}") {
        try {
            timeout(time: "${timeout}", unit: "${timeoutUnit}") {
                sh "docker system prune -f --volumes"
            }
        } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
            // we re-throw as a different error, that would not
            // cause retry() to fail (workaround for issue JENKINS-51454)
            error 'Timeout!'
        }
    } // retry ends
}

/**
 * Usage:
 * <code>
 *     cleanupWorkspace(attempts : 3, timeout : 60, timeoutUnit : 'SECONDS')
 * </code>
 * <p>OR</p>
 * <code>
 *     cleanupWorkspace()
 * </code>
 */
def cleanupWorkspace(int attempts, int timeout = 30, String timeoutUnit = 'SECONDS') {
    retry("${attempts}") {
        try {
            timeout(time: "${timeout}", unit: "${timeoutUnit}") {
                deleteDir() // Clean up workspace
            }
        } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
            // we re-throw as a different error, that would not
            // cause retry() to fail (workaround for issue JENKINS-51454)
            error 'Timeout!'
        }
    } // retry ends
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
 *     defaultPollSCM()
 * </code>
 */
def defaultPollSCM() {
    // Once in every 2 hours slot between 0900 and 1600 every Monday - Friday
    pollSCM('H H(8-16)/2 * * 1-5')
}
