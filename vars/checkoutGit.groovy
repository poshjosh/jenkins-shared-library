#!/usr/bin/env groovy
/**
 * <p>https://github.com/poshjosh</p>
 * Usage:
 * <code>
 *     checkoutGit(gitUrl : 'link_to_your_git_repo_here')
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
