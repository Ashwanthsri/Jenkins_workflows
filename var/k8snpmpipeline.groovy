def call(body) {

  def pipelineParams = [: ]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = pipelineParams
  body()
  //  def packageJSON = ""
  //  def packageJSONVersion = "1.0.0"
  def jobnameparts = JOB_NAME.tokenize('/') as String[]
  def jobconsolename = jobnameparts[0]
  pipeline {
    agent {
      label 'linux'
    }
    environment {
      //VERSION = '1.0.1' //latest
      DEVNS = "sdx-dev"
      DEV1NS = "sdx-dev1"
      QANS = "sdx-qa"
      PRODNS = "sdx-prod"
      DEVDHMPNS = "sdx-dev-dhmp"
      DEMODHMPNS = "sdx-demo-dhmp"
      PRODDHMPNS = "sdx-prod-dhmp"
      QADHMPNS = "sdx-qa-dhmp"
      UATNS = "sdx-qa-denverhealth"
      ECRURL = '233431242547.dkr.ecr.us-east-1.amazonaws.com'
      REPOSITORY_URI = "https://233431242547.dkr.ecr.us-east-1.amazonaws.com"
      PROJECT = "${pipelineParams.appName}" //application name
      ECRREPO = "${ECRURL}/sdx-dev/${pipelineParams.appName}"
      DEVECRREPO = "${ECRURL}/sdx-dev/${pipelineParams.appName}"
      QAECRREPO = "${ECRURL}/sdx-qa/${pipelineParams.appName}"
      PRODECRREPO = "${ECRURL}/sdx-prod/${pipelineParams.appName}"
      DEVDHMPECRREPO = "${ECRURL}/sdx-dev-dhmp/${pipelineParams.appName}"
      DEMODHMPECRREPO = "${ECRURL}/sdx-demo-dhmp/${pipelineParams.appName}"
      PRODDHMPECRREPO = "${ECRURL}/sdx-prod-dhmp/${pipelineParams.appName}"
      QADHMPECRREPO = "${ECRURL}/sdx-qa-dhmp/${pipelineParams.appName}"
      UATECRREPO = "${ECRURL}/sdx-qa-denverhealth/${pipelineParams.appName}"
      ECRCRED = 'ecr:us-east-1:ecr-credentials'
      //IMAGE = "${ECRURL}/sdx-dev/${PROJECT}:${VERSION}"
    }

    tools {
      maven 'Maven3'
      jdk 'jdk1.8.0_141'
    }
    options {
      buildDiscarder(logRotator(numToKeepStr: '5'))
      disableConcurrentBuilds()
      skipDefaultCheckout(false)
      skipStagesAfterUnstable()
      timeout(time: 1, unit: 'HOURS')
    }
    stages {
      
      stage('Cloning Git') {
        steps {
             // Clean before build
             cleanWs()
             // We need to explicitly checkout from SCM here
             checkout scm
             echo "Building ${env.JOB_NAME}..."
             //sh "git pull -f ${env.BRANCH_NAME}"
             //checkout([$class: 'GitSCM', branches: [[name: '*/release_1.0']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: '', url: 'https://github.com/sd031/aws_codebuild_codedeploy_nodeJs_demo.git']]])     
          }
      }
      stage('Build preparations') {
        steps {

          script { 
            echo 'Pulling…' + env.BRANCH_NAME
            sh "git checkout ${env.BRANCH_NAME}"
            sh """
            git status
            npm version patch
            git add -f *
              git push -f origin "${env.BRANCH_NAME}"
            """
            // calculate GIT lastest commit short-hash
            gitCommitHash = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
            shortCommitHash = gitCommitHash.take(7)
            // calculate a sample version tag
            //VERSION = shortCommitHash

            // set the build display name
            def packageJSON = readJSON file: 'package.json'

            env.VERSION = packageJSON.version
            currentBuild.displayName = "#${BUILD_ID}-${env.VERSION}"
            IMAGE = "$PROJECT:$VERSION"
            echo "VERSION:${env.VERSION}"

            echo env.VERSION
            echo "printed version"
            //env.IMAGE = "${env.ECRURL}/sdx-qa/${env.PROJECT}:${env.VERSION}"

          }
        }
      }
	 
     stage('Deploy to Angular-todo') {
        when {
          anyOf {
            branch 'develop'
          }
        }
        steps {
          script {
            //env.IMAGE = "${ECRURL}/sdx-dev1/${PROJECT}:${env.VERSION}"
            //echo env.IMAGE
            //BuildPush("${env.IMAGE}", "dev1")
	    //sh "sed -i 's|{image}|${env.IMAGE}|g' k8s/k8s.yaml"
            //sh "sed -i 's|{ns}|${DEV1NS}|g' k8s/k8s.yaml"
            //sh "sed -i 's|{app_name}|${pipelineParams.appName}|g' k8s/k8s.yaml"
            //sh "sed -i 's|{env}|dev|g' k8s/k8s.yaml"
            //sh "sed -i 's|{nodename}|dev|g' k8s/k8s.yaml"
            //sh "cat k8s/k8s.yaml"
            //kubernetesDeploy configs: 'k8s/k8s.yaml', secretNamespace: "${env.DEVNS}", enableConfigSubstitution: false, kubeConfig: [path: ''], kubeconfigId: 'kubeconfig', secretName: '', ssh: [sshCredentialsId: '*', sshServer: ''], textCredentials: [certificateAuthorityData: '', clientCertificateData: '', clientKeyData: '', serverUrl: 'https://']
            //sh "kubectl config get-contexts"
            // Virginia context
            //sh "kubectl config use-context arn:aws:eks:us-east-1:233431242547:cluster/sdx"
            //mumbai context
            //sh "kubectl config use-context arn:aws:eks:ap-south-1:233431242547:cluster/sdx"
            //sh "kubectl get pods -n  ${DEV1NS}"
            //sh "kubectl get ns"
            //sh "kubectl set image deployment/${PROJECT} -n ${DEV1NS} ${pipelineParams.appName}=${ECRURL}/sdx-dev1/${PROJECT}:${env.VERSION}"
            //sh "kubectl apply -f k8s/k8s.yaml -n  ${DEV1NS}"
            sh """
               kubectl patch deployment "${PROJECT}"  -p   "{\\"spec\\":{\\"template\\":{\\"metadata\\":{\\"annotations\\":{\\"date\\":\\"`date +'%s'`\\"}}}}}" -n "${DEV1NS}" || true
            """
          }
        }

      }
	    
            stage('Static Code Analysis') {
                failFast true
                parallel {
                    stage('SonarQube Analysis & Quality Gate') {
                        steps {
                            sonarQubeScan("${env.JOB_NAME}".tokenize('/')[0], pipelineParams.BUILD_TOOL, pipelineParams.NODE_VERSION)
                           // qualityGate()
                        }
                    }
                    
		    
                    stage("Dependency Check") {
                        when {
                            branch 'Test'
                        }
                        steps {
                            owaspDependencyCheck()
                        }
                    }
                }
            }
            */

    }

    post {
      success {
        sendNotification("PASSED", pipelineParams.JENKINS_NOTIFICATIONS_TO)
      }
      failure {
        sendNotification("FAILED", pipelineParams.JENKINS_NOTIFICATIONS_TO)
      }
      always {
        //sh 'printenv'
        //jiraSendBuildInfo branch: 'master', site: 'servicedx.atlassian.net'
        echo "Build Duration : ${currentBuild.durationString}"
        // junit 'target/surefire-reports/*.xml'
        //archiveArtifacts  "target/**/*"
        deleteDir()
      }
    }
  }
}
