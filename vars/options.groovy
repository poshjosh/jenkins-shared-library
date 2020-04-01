#!/usr/bin/env groovy
/**
 * <p>https://github.com/poshjosh</p>
 * Usage:
 * <code>
 *     options(timeout : 30, timeoutUnit : 'MINUTES', numberOfBuildsToKeep : 5)
 * </code>
 * <p>OR</p>
 * <code>
 *     options()
 * </code>
 */
def call(int timeout = 30, String timeoutUnit = 'MINUTES', numberOfBuildsToKeep = 5) {
    options {
        timestamps()
        timeout(time: "${config.timeout}", unit: "${config.timeoutUnit}")
        buildDiscarder(logRotator(numToKeepStr: '4'))
        skipStagesAfterUnstable()
        disableConcurrentBuilds()
    }
}
