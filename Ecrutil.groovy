import com.regeneron.radit.devops.Util
import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonSlurperClassic

def ecrURI = null

def ecrBuildPublish(Map params) {
    def repoURL = (params.repoURL) ? params.repoURL : "default"
    def ecrRepoName = params.ecrRepoName
    def dockerfileName = (params.dockerfileName) ? params.dockerfileName : "Dockerfile"
    def dockerfilePath = (params.dockerfilePath) ? params.dockerfilePath : "."
    def tags = (params.tags) ? params.tags : ['latest']
    def dockerArgs = (params.dockerArgs) ? params.dockerArgs : ""
    def customAction = params.customAction ? params.customAction : { println "No custom action" }
    def credentialsId = params.credentialsId ? params.credentialsId : []
    def sshKeyCredential = params.sshKeyCredential
    def awsRegion = (params.awsRegion) ? params.awsRegion : 'us-east-1'

    ecrBuildPublish(repoURL, ecrRepoName, dockerfileName, dockerfilePath, tags, dockerArgs, customAction, credentialsId, sshKeyCredential, awsRegion)
}

def ecrBuildPublish(String repoURL = "default", String ecrRepoName, String dockerfileName = "Dockerfile", String dockerfilePath = ".", List<String> tags = ['latest'], String dockerArgs = "", Closure customAction = { println "No custom action" }, List<String> credentialsId = [], String sshKeyCredential, String awsRegion = 'us-east-1') {
    def awsAccounts = [145032978487, 154919775133, 486047195917, 534307230316, 293693214045, 694928589017]

    if (repoURL == "default") {
        def accountID = (env.BRANCH_NAME == 'master' || env.GIT_BRANCH == 'origin/master') ? 145032978487 : 154919775133
        def ecrURL = accountID + ".dkr.ecr." + awsRegion + ".amazonaws.com/"
        ecrURI = ecrURL + ecrRepoName
        registry = "https://" + ecrURI
    } else if (repoURL =~ /.*\.amazonaws.com.*/) {
        def accountID = repoURL.split('\\.')[0]
        def ecrURL = repoURL
        ecrURI = repoURL.endsWith("/") ? (repoURL + ecrRepoName) : (repoURL + "/" + ecrRepoName)
        registry = "https://" + ecrURI
    } else {
        if (repoURL =~ /.*hub\.docker\.com.*/) {
            ecrURI = ecrRepoName
            registry = ''
        } else {
            ecrURI = repoURL.endsWith("/") ? (repoURL + ecrRepoName) : (repoURL + "/" + ecrRepoName)
            registry = "https://" + ecrURI
        }
    }

    node('devops-docker-build') {
        checkout scm
        println("ecrURI=${ecrURI}")
        println("registry=${registry}")

        // Invoke additional custom code if provided
        customAction()

        def firstArg = ecrURI
        def secondArg = (dockerArgs?.trim()) ? "${dockerArgs} -f ${dockerfileName} ${dockerfilePath}" : "-f ${dockerfileName} ${dockerfilePath}"

        // Login ECR service
        awsAccounts.each { account ->
            sh """
            aws ecr get-login-password --region $awsRegion |
            docker login --username AWS --password-stdin ${account}.dkr.ecr.${awsRegion}.amazonaws.com
            """
        }

        credentialsId.each { credential ->
            def idCustom = credential.split(',')[0]
            def urlCustom = credential.split(',')[1]

            if (urlCustom =~ /.*hub.docker.com.*/) {
                uriCustom = ''
            }

            withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: idCustom, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
                sh "docker login -u '${env.USERNAME}' -p '${env.PASSWORD}' ${urlCustom}"
            }
        }

        def uniqueRepoName = UUID.randomUUID().toString()
        println("Building docker image ${ecrRepoName}, using unique name '${uniqueRepoName}'")

        def newImage
        if (sshKeyCredential?.trim()) {
            sshagent(credentials: [sshKeyCredential]) {
                env.DOCKER_BUILDKIT = 1
                secondArg = "--ssh=default ${secondArg}"
                newImage = docker.build(uniqueRepoName, secondArg)
            }
        } else {
            newImage = docker.build(uniqueRepoName, secondArg)
        }

        // Publish image
        docker.withRegistry(registry) {
            sh("printenv | sort")
            for (tag in tags) {
                sh """
                #!/bin/bash -ex
                docker tag ${uniqueRepoName} ${firstArg}:${tag}
                docker push ${firstArg}:${tag}
                """
            }
        }
    }
}

def ecrBuildPublishKaniko(Map params) {
    def repoURL = (params.repoURL) ? params.repoURL : "default"
    def ecrRepoName = params.ecrRepoName
    def dockerfileName = (params.dockerfileName) ? params.dockerfileName : "Dockerfile"
    def dockerfilePath = (params.dockerfilePath) ? params.dockerfilePath : "."
    def tags = (params.tags) ? params.tags : ['latest']
    def dockerArgs = (params.dockerArgs) ? params.dockerArgs : ""
    def customAction = params.customAction ? params.customAction : { println "No custom action" }
    def credentialsId = params.credentialsId ? params.credentialsId : []
    def sshKeyCredential = params.sshKeyCredential
    def awsRegion = (params.awsRegion) ? params.awsRegion : 'us-east-1'
    def useCache = params.useCache != null ? params.useCache : "true"

    ecrBuildPublishKaniko(repoURL, ecrRepoName, dockerfileName, dockerfilePath, tags, dockerArgs, customAction, credentialsId, sshKeyCredential, awsRegion, useCache)
}

def ecrBuildPublishKaniko(String repoURL = "default", String ecrRepoName, String dockerfileName = "Dockerfile", String dockerfilePath = ".", List<String> tags = ['latest'], String dockerArgs = "", Closure customAction = { println "No custom action" }, List<String> credentialsId = [], String sshKeyCredential, String awsRegion = 'us-east-1', String useCache = "true") {
    def awsAccounts = [145032978487, 154919775133, 486047195917, 534307230316, 293693214045]

    def kanikoOptions = (useCache == "true") ? "" : "--ignore-path=/var/mail --ignore-path=/var/spool/mail --insecure-skip-tls-verify --no-cache"

    if (repoURL == "default") {
        def accountID = (env.BRANCH_NAME == 'master' || env.GIT_BRANCH == 'origin/master') ? 145032978487 : 154919775133
        def ecrURL = accountID + ".dkr.ecr." + awsRegion + ".amazonaws.com/"
        ecrURI = ecrURL + ecrRepoName
        registry = "https://" + ecrURL + ecrRepoName
    } else if (repoURL =~ /.*\.amazonaws.com.*/) {
        def accountID = repoURL.split('\\.')[0]
        def ecrURL = repoURL
        ecrURI = repoURL.endsWith("/") ? (repoURL + ecrRepoName) : (repoURL + "/" + ecrRepoName)
        registry = "https://" + ecrURI
    } else {
        if (repoURL =~ /.*hub.docker.com.*/) {
            ecrURI = ecrRepoName
            registry = ""
        } else {
            ecrURI = repoURL.endsWith("/") ? (repoURL + ecrRepoName) : (repoURL + "/" + ecrRepoName)
            registry = "https://" + ecrURI
        }
    }

    println("ecrURI=${ecrURI}")
    println("registry=${registry}")

    def kanikoStatus = sh(returnStdout: true, script: '''
    if [ -f /kaniko/executor ]; then echo "0"; else echo "1"; fi
    ''').trim()

    if (kanikoStatus != '0') {
        println("Building using KANIKO")
        def podLabel = "kaniko-pod"
        def podTemplate = "kaniko"
        def tagDestinations = tags.collect { "--destination='${ecrURI}:${it}'" }.join(' ')

        podTemplate(label: podLabel, inheritFrom: podTemplate) {
            node(podLabel) {
                checkout scm

                kanikoStatus = sh(returnStdout: true, script: '''
                if [ -f /kaniko/executor ]; then echo "0"; else echo "1"; fi
                ''').trim()

                customAction()

                if (kanikoStatus != '0') {
                    throw new Exception('Kaniko executor is not present, check podtemplate')
                }

                awsAccounts.each { account ->
                    sh """
                    aws ecr get-login-password --region $awsRegion |
                    docker login --username AWS --password-stdin ${account}.dkr.ecr.${awsRegion}.amazonaws.com
                    """
                }

                credentialsId.each { credential ->
                    def idCustom = credential.split(',')[0]
                    def urlCustom = credential.split(',')[1]

                    if (urlCustom =~ /.*hub.docker.com.*/) {
                        uriCustom = ''
                    }

                    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: idCustom, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
                        sh "docker login -u '${env.USERNAME}' -p '${env.PASSWORD}' ${urlCustom}"
                    }
                }

                container(name: 'kaniko', shell: '/busybox/sh') {
                    sshagent(credentials: [sshKeyCredential]) {
                        sh """
                        /kaniko/executor --context=dir://workspace/ --dockerfile=${dockerfilePath}/${dockerfileName} ${kanikoOptions} ${tagDestinations} ${dockerArgs}
                        """
                    }
                }
            }
        }
    } else {
        println("Kaniko executor is not present in the cluster, use other steps to build the image")
    }
}

def ecrRetag(String sourceURI, String tagSource, List<String> tagDestinations, String credentialsId, String awsRegion = 'us-east-1') {
    node {
        docker.withRegistry("https://${sourceURI}", credentialsId) {
            for (tagDestination in tagDestinations) {
                sh """
                docker pull ${sourceURI}:${tagSource}
                docker tag ${sourceURI}:${tagSource} ${sourceURI}:${tagDestination}
                docker push ${sourceURI}:${tagDestination}
                """
            }
        }
    }
}

def checkImage(String repoURL, String tag = "latest") {
    def repo = repoURL.split('/').last()
    def image = repo.split(':').first()
    tag = repo.split(':').last() ? repo.split(':').last() : tag

    def statusCode = sh(returnStatus: true, script: """
    curl -s -o /dev/null -w "%{http_code}" https://hub.docker.com/v2/repositories/${repo}/tags/${tag}
    """)

    if (statusCode == 404) {
        error("Image not found: ${repoURL}:${tag}")
    } else {
        println("Image found: ${repoURL}:${tag}")
    }
}

def exportImage(String imageName, String imageTag = "latest", String awsRegion = 'us-east-1', String credentialsId, String filePath) {
    node {
        docker.withRegistry("https://${imageName}", credentialsId) {
            sh """
            docker pull ${imageName}:${imageTag}
            docker save ${imageName}:${imageTag} -o ${filePath}
            """
        }
    }
}
