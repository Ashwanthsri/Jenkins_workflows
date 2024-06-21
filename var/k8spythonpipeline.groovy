
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
      label 'LINUX'
    }
    environment {
      //VERSION = '1.0.1' //latest
      DEVNS = "sdx-ssai-dev"
      QANS = "sdx-ssai-qa"
      DEMONS = "sdx-demo"
      PRODNS = "sdx-prod"
      DEMOUSNS = "sdx-demo-us"	    
      ECRURL = '233431242547.dkr.ecr.ap-south-1.amazonaws.com'
      REPOSITORY_URI = "https://233431242547.dkr.ecr.ap-south-1.amazonaws.com"
      PROJECT = "${pipelineParams.appName}" //application name
      ECRREPO = "${ECRURL}/sdx-dev/${pipelineParams.appName}"
      DEVECRREPO = "${ECRURL}/sdx-dev/${pipelineParams.appName}"
      QAECRREPO = "${ECRURL}/sdx-qa/${pipelineParams.appName}"
      DEMOECRREPO = "${ECRURL}/sdx-demo/${pipelineParams.appName}"
      PRODECRREPO = "${ECRURL}/sdx-prod/${pipelineParams.appName}"
      DEMOUSECRREPO = "${ECRURL}/sdx-demo-us/${pipelineParams.appName}"
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

      stage('Build preparations') {
        steps {

          script {
	    System.setProperty("org.jenkinsci.plugins.durabletask.BourneShellScript.HEARTBEAT_CHECK_INTERVAL", "3800");
            // calculate GIT lastest commit short-hash
            gitCommitHash = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
            shortCommitHash = gitCommitHash.take(7)
            // calculate a sample version tag
            env.VERSION = shortCommitHash

            // set the build display name
            //def packageJSON = readJSON file: 'package.json'

            //env.VERSION = packageJSON.version
            currentBuild.displayName = "#${BUILD_ID}-${env.VERSION}"
            IMAGE = "$PROJECT:$VERSION"
            echo "VERSION:${env.VERSION}"

            echo env.VERSION
            echo "printed version"
            //env.IMAGE = "${env.ECRURL}/sdx-qa/${env.PROJECT}:${env.VERSION}"

          }
        }
      }

      
      stage('Deploy to Dev') {
        when {
          anyOf {
            branch 'dev';
            branch 'develop'
	    branch 'main'
          }
        }
        steps {
          script {
            env.IMAGE = "${ECRURL}/sdx-dev/${PROJECT}:${env.VERSION}"
            echo env.IMAGE
            BuildPush("${env.IMAGE}", "dev")
            sh "sed -i 's|{image}|${env.IMAGE}|g' k8s/k8s.yaml"
            sh "sed -i 's|{ns}|${DEVNS}|g' k8s/k8s.yaml"
            sh "sed -i 's|{app_name}|${pipelineParams.appName}|g' k8s/k8s.yaml"
            sh "sed -i 's|{env}|dev|g' k8s/k8s.yaml"
            sh "sed -i 's|{nodename}|qa|g' k8s/k8s.yaml"
            sh "cat k8s/k8s.yaml"
            //sh "sed -i 's|ACCOUNT|${ACCOUNT}|g' k8s/service.yaml"
            //sh "sed -i 's|ENVIRONMENT|dev|g' k8s/dev/*.yaml"
            // sh "sed -i 's|BUILD_NUMBER|01|g' k8s/dev/*.yaml"
            //sh 'cat k8s/k8s.yaml'
            sh "kubectl config use-context arn:aws:eks:ap-south-1:233431242547:cluster/sdx"
            sh "kubectl apply -f k8s/k8s.yaml -n  ${DEVNS}"

            sh """
               kubectl patch deployment "${PROJECT}"  -p   "{\\"spec\\":{\\"template\\":{\\"metadata\\":{\\"annotations\\":{\\"date\\":\\"`date +'%s'`\\"}}}}}" -n "${DEVNS}" || true
            """

            //kubernetesDeploy configs: 'k8s/k8s.yaml', secretNamespace: "${env.DEVNS}", enableConfigSubstitution: false, kubeConfig: [path: ''], kubeconfigId: 'kubeconfig', secretName: '', ssh: [sshCredentialsId: '*', sshServer: ''], textCredentials: [certificateAuthorityData: '', clientCertificateData: '', clientKeyData: '', serverUrl: 'https://']
            sh "kubectl delete pods -l app=sdx-recordai-table-detection -n  ${DEVNS}"
          }
        }

      }

      /*
       stage('Arroval for QA Deployment') {
            when {
      	branch 'qa'
            }
            steps {
      	script {
      	  env.ARROVAL_ON_QA_DEPLOY = input message: 'User input required',
      	      parameters: [choice(name: 'Deployment on QA Environment', choices: 'no\nyes', description: 'Choose "yes" if you want to deploy this build on QA Environment')]
      	}
            }
          }
      */
	stage('Deploy to Demous') {
		when {
		  anyOf {
		    branch 'developV2_QA'
		  }
		}
		steps {
		  script {
		    env.IMAGE = "${ECRURL}/sdx-demo-us/${PROJECT}:${env.VERSION}"
		    echo env.IMAGE
		    BuildPush("${env.IMAGE}", "dev")
		    sh "sed -i 's|{image}|${env.IMAGE}|g' k8s/k8s.yaml"
		    sh "sed -i 's|{ns}|${DEMOUSNS}|g' k8s/k8s.yaml"
		    sh "sed -i 's|{app_name}|${pipelineParams.appName}|g' k8s/k8s.yaml"
		    sh "sed -i 's|{env}|demo-us|g' k8s/k8s.yaml"
		    sh "sed -i 's|{nodename}|dev|g' k8s/k8s.yaml"
		    sh "cat k8s/k8s.yaml"
		    //sh "sed -i 's|ACCOUNT|${ACCOUNT}|g' k8s/service.yaml"
		    //sh "sed -i 's|ENVIRONMENT|demo-us|g' k8s/dev/*.yaml"
		    // sh "sed -i 's|BUILD_NUMBER|01|g' k8s/dev/*.yaml"
		    //sh 'cat k8s/k8s.yaml'
		    sh "kubectl config use-context arn:aws:eks:ap-south-1:233431242547:cluster/sdx"
		    sh "kubectl apply -f k8s/k8s.yaml -n  ${DEMOUSNS}"

		    sh """
		       kubectl patch deployment "${jobconsolename}"  -p   "{\\"spec\\":{\\"template\\":{\\"metadata\\":{\\"annotations\\":{\\"date\\":\\"`date +'%s'`\\"}}}}}" -n "${DEMOUSNS}" || true
		    """

		    //kubernetesDeploy configs: 'k8s/k8s.yaml', secretNamespace: "${env.DEVNS}", enableConfigSubstitution: false, kubeConfig: [path: ''], kubeconfigId: 'kubeconfig', secretName: '', ssh: [sshCredentialsId: '*', sshServer: ''], textCredentials: [certificateAuthorityData: '', clientCertificateData: '', clientKeyData: '', serverUrl: 'https://']

		  }
		}

	      }

      stage('Deploy to QA') {

        when {
          anyOf {
            branch 'qa';
            branch 'uat'
            //environment name: 'ARROVAL_ON_QA_DEPLOY', value: 'yes'
          }
        }
        steps {
          script {
            env.IMAGE = "${ECRURL}/sdx-qa/${PROJECT}:${env.VERSION}"
            //BuildPush("${QAECRREPO}")
            BuildPush("${env.IMAGE}", "qa")
            sh "sed -i 's|{image}|${env.IMAGE}|g' k8s/k8s.yaml"
            sh "sed -i 's|{ns}|${QANS}|g' k8s/k8s.yaml"
            sh "sed -i 's|{app_name}|${pipelineParams.appName}|g' k8s/k8s.yaml"
            sh "sed -i 's|{env}|qa|g' k8s/k8s.yaml"
            sh "sed -i 's|{nodename}|dev|g' k8s/k8s.yaml"
            sh "cat k8s/k8s.yaml"
            //sh "sed -i 's|ACCOUNT|${ACCOUNT}|g' k8s/service.yaml"
            //sh "sed -i 's|ENVIRONMENT|dev|g' k8s/dev/*.yaml"
            // sh "sed -i 's|BUILD_NUMBER|01|g' k8s/dev/*.yaml"
            sh 'cat k8s/k8s.yaml'
            sh "kubectl config use-context arn:aws:eks:ap-south-1:233431242547:cluster/sdx"
            sh "kubectl apply -f k8s/k8s.yaml -n  ${QANS}"

            sh """
               kubectl patch deployment "${jobconsolename}"  -p   "{\\"spec\\":{\\"template\\":{\\"metadata\\":{\\"annotations\\":{\\"date\\":\\"`date +'%s'`\\"}}}}}" -n "${QANS}" || true 
	    """
            //kubernetesDeploy configs: 'k8s/k8s.yaml', secretNamespace: "${env.QANS}", enableConfigSubstitution: false, kubeConfig: [path: ''], kubeconfigId: 'kubeconfig', secretName: '', ssh: [sshCredentialsId: '*', sshServer: ''], textCredentials: [certificateAuthorityData: '', clientCertificateData: '', clientKeyData: '', serverUrl: 'https://']

          }
        }

      }

      stage('build image for demo') {

        when {
          anyOf {
            branch 'demo_10thAugust_working'
          }
        }
        steps {
          script {
            env.IMAGE = "${ECRURL}/sdx-demo/${PROJECT}:${env.VERSION}"
            //BuildPush("${QAECRREPO}")
            BuildPush("${env.IMAGE}", "qa")
            //  sh "sed -i 's|{image}|${env.IMAGE}|g' k8s/k8s.yaml"
            // sh "sed -i 's|{ns}|${QANS}|g' k8s/k8s.yaml"
            // sh "sed -i 's|{app_name}|${pipelineParams.appName}|g' k8s/k8s.yaml"
            // sh "sed -i 's|{env}|qa|g' k8s/k8s.yaml"
            //sh "sed -i 's|{nodename}|dev|g' k8s/k8s.yaml"
            // sh "cat k8s/k8s.yaml"
            //sh "sed -i 's|ACCOUNT|${ACCOUNT}|g' k8s/service.yaml"
            //sh "sed -i 's|ENVIRONMENT|dev|g' k8s/dev/*.yaml"
            // sh "sed -i 's|BUILD_NUMBER|01|g' k8s/dev/*.yaml"
            //sh 'cat k8s/k8s.yaml'
            //sh "kubectl apply -f k8s/k8s.yaml -n  ${QANS}"

            // sh """
            //  kubectl patch deployment "${jobconsolename}"  -p   "{\\"spec\\":{\\"template\\":{\\"metadata\\":{\\"annotations\\":{\\"date\\":\\"`date +'%s'`\\"}}}}}" -n "${QANS}" || true
            //"""
            //kubernetesDeploy configs: 'k8s/k8s.yaml', secretNamespace: "${env.QANS}", enableConfigSubstitution: false, kubeConfig: [path: ''], kubeconfigId: 'kubeconfig', secretName: '', ssh: [sshCredentialsId: '*', sshServer: ''], textCredentials: [certificateAuthorityData: '', clientCertificateData: '', clientKeyData: '', serverUrl: 'https://']

          }
        }

      }

      stage('Arroval for DEMO Deployment') {
        when {
          branch 'demo'
        }
        steps {
          script {
            env.ARROVAL_ON_DEMO_DEPLOY = input message: 'User input required',
              parameters: [choice(name: 'Deployment on DEMO Environment', choices: 'no\nyes', description: 'Choose "yes" if you want to deploy this build on DEMO Environment')]
          }
        }
      }

      stage('Deploy to DEMO') {

        when {
          anyOf {
            branch 'demo'
            environment name: 'ARROVAL_ON_DEMO_DEPLOY', value: 'yes'
          }
        }
        steps {
          script {
            env.IMAGE = "${ECRURL}/sdx-demo/${PROJECT}:${env.VERSION}"
            //BuildPush("${QAECRREPO}")
            BuildPush("${env.IMAGE}", "demo")
            sh "sed -i 's|{image}|${env.IMAGE}|g' k8s/k8s.yaml"
            sh "sed -i 's|{ns}|${DEMONS}|g' k8s/k8s.yaml"
            sh "sed -i 's|{app_name}|${pipelineParams.appName}|g' k8s/k8s.yaml"
            sh "sed -i 's|{env}|demo|g' k8s/k8s.yaml"
            sh "sed -i 's|{nodename}|dev|g' k8s/k8s.yaml"
            sh "cat k8s/k8s.yaml"
            //sh "sed -i 's|ACCOUNT|${ACCOUNT}|g' k8s/service.yaml"
            //sh "sed -i 's|ENVIRONMENT|dev|g' k8s/dev/*.yaml"
            // sh "sed -i 's|BUILD_NUMBER|01|g' k8s/dev/*.yaml"
            sh 'cat k8s/k8s.yaml'
            sh "kubectl config use-context arn:aws:eks:ap-south-1:233431242547:cluster/sdx"
            sh "kubectl apply -f k8s/k8s.yaml -n  ${DEMONS}"

            sh """
            kubectl patch deployment "${jobconsolename}"  -p   "{\\"spec\\":{\\"template\\":{\\"metadata\\":{\\"annotations\\":{\\"date\\":\\"`date +'%s'`\\"}}}}}" -n "${DEMONS}" || true
            """
            //kubernetesDeploy configs: 'k8s/k8s.yaml', secretNamespace: "${env.QANS}", enableConfigSubstitution: false, kubeConfig: [path: ''], kubeconfigId: 'kubeconfig', secretName: '', ssh: [sshCredentialsId: '*', sshServer: ''], textCredentials: [certificateAuthorityData: '', clientCertificateData: '', clientKeyData: '', serverUrl: 'https://']

          }
        }

      }
      /*
		 stage('Deploy to prod') {
			
          when {
                anyOf { 
			branch 'master1'; branch 'prod1' ; branch 'master11' ;branch '1.0.x1' ; branch 'cholayil-1.0.x1'
					}
            }
                   steps {
                        script {
						   env.IMAGE = "${ECRURL}/sdx-prod/${PROJECT}:${env.VERSION}"
						      //BuildPush("${QAECRREPO}")
							  BuildPush("${env.IMAGE}", "prod")
                                                         
                       
				 }
				}
		 
				} 
		
           */
      /*
            
             stage('Deploy Docker Img to prod/cert server'){
                when {
                       branch 'master'
                     }
                steps {
                //label, org, name, innerPort, outerPort, imageTag
                    //deploy(pipelineParams.developmentServer, pipelineParams.serverPort)
                    echo "Deploying App: " + PROJECT
                    dockerDeploy(env.PROJECT,pipelineParams.productionServer, pipelineParams.innerPort, pipelineParams.outerPort, pipelineParams.args)
                   
                }
            }
			*/
      /*
      stage ('Unit & Integratino Tests') {
          steps {
              parallel (
                  "unit tests": { sh 'mvn test' },
                  "integration tests": { sh 'mvn integration-test' }
              )
          }
      }
      */

      /*
      stage("Unit Test") {
          steps {
              executeUnitTests(pipelineParams.BUILD_TOOL, pipelineParams.NODE_VERSION)
          }
      }
      */

      stage('Static Code Analysis') {
        failFast true
        parallel {
          stage('SonarQube Analysis & Quality Gate') {
            steps {
              // sonarQubeScan("${env.JOB_NAME}".tokenize('/')[0], pipelineParams.BUILD_TOOL, pipelineParams.NODE_VERSION)
              // qualityGate()
              echo " sonar scan has been disabled"
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

      /*
      stage('Deploy to Dev space V1') {
          when {
              branch 'develop'
          }
          steps {
              pushToCloudFoundryServerWithRoute(pipelineParams.CF_API, pipelineParams.CF_ORG, pipelineParams.CF_SPACE, pipelineParams.CF_DOMAIN_NAME, pipelineParams.CF_HOST_NAME, pipelineParams.CF_CREDENTIALS)
          }
      }
      */
      /*
        stage('Integration tests') {
            when {
                branch 'develop'
            }
            steps {
                sleep 120
                executeIntegrationTests(pipelineParams.BUILD_TOOL, pipelineParams.NODE_VERSION)
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
