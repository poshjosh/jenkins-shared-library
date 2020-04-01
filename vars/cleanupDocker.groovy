#!/usr/bin/env groovy
/**
 * <p>https://github.com/poshjosh</p>
 * Usage:
 * <code>
 *     cleanupDocker(attempts : 3, timeout : 60, timeoutUnit : 'SECONDS')
 * </code>
 * <p>OR</p>
 * <code>
 *     cleanupDocker()
 * </code>
 */
def call(int attempts, int timeout = 30, String timeoutUnit = 'SECONDS') {
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
