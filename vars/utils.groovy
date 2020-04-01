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
