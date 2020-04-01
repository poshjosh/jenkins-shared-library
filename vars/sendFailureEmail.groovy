#!/usr/bin/env groovy
/**
 * <p>https://github.com/poshjosh</p>
 * Usage:
 * <code>
 *     sendFailureEmail(failureEmailRecipient : 'put_recipient_email_address_here')
 * </code>
 */
def call(String failureEmailRecipient) {
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
