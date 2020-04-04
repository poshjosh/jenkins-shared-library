## jenkins-shared-library

Sample Jenkinsfile using this library

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

The pipeline referenced by ```pipelineForMaven()``` above will:

- Use a default Dockerfile if none is found in the current directory

- Present parameters to the users if no jenkins UI

- Use sensible default parameters
