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
def call(String gitUrl) {
    echo "Git URL: ${gitUrl}"
    checkout([$class: 'GitSCM',
        branches: [[name: '**']],
        doGenerateSubmoduleConfigurations: false,
        extensions: [],
        submoduleCfg: [],
        userRemoteConfigs: [[url: "${gitUrl}"]]
    ])
}
