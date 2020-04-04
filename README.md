## jenkins-shared-library

#### Sample Jenkinsfiles using this library ####

Use pipeline customized for maven
```
#!/usr/bin/env groovy
library(
    identifier: 'jenkins-shared-library@master',
    retriever: modernSCM(
        [
            $class: 'GitSCMSource',
            remote: 'https://github.com/poshjosh/jenkins-shared-library.git'
        ]
    )
)

pipelineForMaven()
```

Use pipeline customized for java and spring boot
```
#!/usr/bin/env groovy
library(
    identifier: 'jenkins-shared-library@master',
    retriever: modernSCM(
        [
            $class: 'GitSCMSource',
            remote: 'https://github.com/poshjosh/jenkins-shared-library.git'
        ]
    )
)

pipelineForJavaSpringBoot(
    appPort : '8093',
    appEndpoint : '/actuator/health',
    mainClass : 'com.my.MainClass'
)
```

The pipelines referenced above will:

- Use a default Dockerfile if none is found in the current directory

- Present parameters to the user if on jenkins UI

- Use sensible default parameters
