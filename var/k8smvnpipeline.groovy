
def call(body) {

  def pipelineParams = [: ]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = pipelineParams
  body()
  def jobnameparts = JOB_NAME.tokenize('/') as String[]
  def jobconsolename = jobnameparts[0]
  pipeline {
    agent {
      label 'linux'
    }
    environment {
      VERSION = ""
      DEVNS = "sdx-dev"
      QANS = "sdx-qa"
      QADEMONS = "sdx-qa-demo"
      PRODNS = "sdx-prod"
      PRODDHMPNS = "sdx-prod-denverhealth"
      QARECORDAINS = "sdx-recordai-qa"
      ECRURL = '233431242547.dkr.ecr.ap-south-1.amazonaws.com'
      USECRURL = '233431242547.dkr.ecr.us-east-1.amazonaws.com'
      REPOSITORY_URI = "https://233431242547.dkr.ecr.ap-south-1.amazonaws.com"
      PROJECT = "${pipelineParams.appName}" //application name
      ECRREPO = "${ECRURL}/sdx-dev/${pipelineParams.appName}"
      DEVECRREPO = "${ECRURL}/sdx-dev/${pipelineParams.appName}"
      QAECRREPO = "${ECRURL}/sdx-qa/${pipelineParams.appName}"
      QADEMOECRREPO = "${ECRURL}/sdx-qa-demo/${pipelineParams.appName}"	    
      PRODECRREPO = "${ECRURL}/sdx-prod/${pipelineParams.appName}"
      QARECORDAIECRREPO = "${ECRURL}/sdx-recordai-qa/${pipelineParams.appName}"
      PRODDHMPECRREPO = "${USECRURL}/sdx-prod-denverhealth/${pipelineParams.appName}"
      ECRCRED = 'ecr:south-1:ecr-credentials'

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

      stage('Build with unit testing') {
        steps {
          // Run the maven build
          script {
            // Get the Maven tool.
            // ** NOTE: This 'M3' Maven tool must be configured
            // **       in the global configuration.
            echo 'Pulling…' + env.BRANCH_NAME
            sh "git checkout ${env.BRANCH_NAME}"
            sh "mvn --batch-mode release:update-versions"
            sh """
            git add .
            git commit -m "push pom file to git"
            git push -f origin "${env.BRANCH_NAME}"
            """

            def mvnHome = tool 'Maven3'
            if (isUnix()) {
              def targetVersion = getDevVersion()
              print 'target build version…'
              print targetVersion
              sh "'${mvnHome}/bin/mvn' -Dintegration-tests.skip=true -Dmaven.test.skip=true -Dbuild.number=${targetVersion} clean package -U"
              def pom = readMavenPom file: 'pom.xml'
              // get the current development version 
              //developmentArtifactVersion = "${pom.version}–${targetVersion}"
              VERSION = "${pom.version}"
              print pom.version
              // execute the unit testing and collect the reports
              //junit '**//*target/surefire-reports/TEST-*.xml'
              //archive 'target*//*.jar'
              // added to unpack the fat jar - spring boot 2.3.0 docker enhancement    
              sh 'mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)'
            } else {
              bat(/"${mvnHome}\bin\mvn" -Dintegration-tests.skip=true clean package/)
              def pom = readMavenPom file: 'pom.xml'
              print pom.version
              //junit '**//*target/surefire-reports/TEST-*.xml'
              //archive 'target*//*.jar'
            }
          }

        }
      }

      stage('JaCoCo Report') {
        steps {

          echo "Running Jacco Reports"
          jacoco exclusionPattern: '**/test/**,**/lib/*', inclusionPattern: '**/*.class,**/*.java'
        }
      }

      stage('Deploy to Dev') {
        when {
          anyOf {
            branch 'develop';
            branch 'prod';
            branch 'developV2'
          }
        }
        steps {
          script {
            env.IMAGE = "${ECRURL}/sdx-dev/${PROJECT}:${VERSION}"
            BuildPush("${env.IMAGE}")
            sh "sed -i 's|{image}|${env.IMAGE}|g' k8s/k8s.yaml"
            sh "sed -i 's|{ECRURL}|${ECRURL}|g' k8s/k8s.yaml"
            sh "sed -i 's|{ns}|${DEVNS}|g' k8s/k8s.yaml"
            sh "sed -i 's|{app_name}|${pipelineParams.appName}|g' k8s/k8s.yaml"
            sh "sed -i 's|{env}|dev|g' k8s/k8s.yaml"
            sh "sed -i 's|{nodename}|dev|g' k8s/k8s.yaml"
            sh "sed -i 's|{platform}|apps|g' k8s/k8s.yaml"
            sh "cat k8s/k8s.yaml"
            //sh "sed -i 's|ACCOUNT|${ACCOUNT}|g' k8s/service.yaml"
            //sh "sed -i 's|ENVIRONMENT|dev|g' k8s/dev/*.yaml"
            // sh "sed -i 's|BUILD_NUMBER|01|g' k8s/dev/*.yaml"
            sh "kubectl config use-context arn:aws:eks:ap-south-1:233431242547:cluster/sdx"
            sh "kubectl apply -f k8s/k8s.yaml -n  ${DEVNS}"
            sh """
               kubectl patch deployment "${PROJECT}"  -p   "{\\"spec\\":{\\"template\\":{\\"metadata\\":{\\"annotations\\":{\\"date\\":\\"`date +'%s'`\\"}}}}}" -n "${DEVNS}" || true
	    """

            // kubernetesDeploy configs: 'k8s/k8s.yaml', secretNamespace: "${env.DEVNS}", enableConfigSubstitution: false, kubeConfig: [path: ''], kubeconfigId: 'kubeconfig', secretName: '', ssh: [sshCredentialsId: '*', sshServer: ''], textCredentials: [certificateAuthorityData: '', clientCertificateData: '', clientKeyData: '', serverUrl: 'https://']

          }
        }

      }
  stage('Deploy to QA-recordai') {
        when {
          anyOf {
            branch 'womba'
          }
        }
        steps {
          script {
            env.IMAGE = "${USECRURL}/sdx-recordai-qa/${PROJECT}:${VERSION}"
            echo env.IMAGE
            BuildPush("${env.IMAGE}")
            sh "sed -i 's|{image}|${env.IMAGE}|g' k8s/k8s.yaml"
            sh "sed -i 's|{ECRURL}|${USECRURL}|g' k8s/k8s.yaml"
            sh "sed -i 's|{ns}|${QARECORDAINS}|g' k8s/k8s.yaml"
            sh "sed -i 's|{app_name}|${pipelineParams.appName}|g' k8s/k8s.yaml"
            sh "sed -i 's|{env}|qa|g' k8s/k8s.yaml"
            sh "sed -i 's|{nodename}|qa|g' k8s/k8s.yaml"
	    sh "sed -i 's|{platform}|recordai-apps|g' k8s/k8s.yaml"
            sh "cat k8s/k8s.yaml"
            //sh "sed -i 's|ACCOUNT|${ACCOUNT}|g' k8s/service.yaml"
            //sh "sed -i 's|ENVIRONMENT|qa|g' k8s/dev/*.yaml"
            // sh "sed -i 's|BUILD_NUMBER|01|g' k8s/dev/*.yaml"
            sh "kubectl config use-context arn:aws:eks:us-east-1:233431242547:cluster/sdx"
            sh "kubectl apply -f k8s/k8s.yaml -n  ${QARECORDAINS}"
            sh """
               kubectl patch deployment "${PROJECT}"  -p   "{\\"spec\\":{\\"template\\":{\\"metadata\\":{\\"annotations\\":{\\"date\\":\\"`date +'%s'`\\"}}}}}" -n "${QARECORDAINS}" || true
	    """

            // kubernetesDeploy configs: 'k8s/k8s.yaml', secretNamespace: "${env.QARECORDAINS}", enableConfigSubstitution: false, kubeConfig: [path: ''], kubeconfigId: 'kubeconfig', secretName: '', ssh: [sshCredentialsId: '*', sshServer: ''], textCredentials: [certificateAuthorityData: '', clientCertificateData: '', clientKeyData: '', serverUrl: 'https://']

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

      stage('Deploy to QA') {

        when {
          anyOf {
            branch 'stv-emergency-patch';
            branch 'qa';
            branch 'uat';
            branch 'stvincent'
            //environment name: 'ARROVAL_ON_QA_DEPLOY', value: 'yes'
          }
        }
        steps {
          script {
            env.IMAGE = "${ECRURL}/sdx-qa/${PROJECT}:${VERSION}"
            BuildPush("${env.IMAGE}")
            sh "sed -i 's|{image}|${env.IMAGE}|g' k8s/k8s.yaml"
            sh "sed -i 's|{ECRURL}|${ECRURL}|g' k8s/k8s.yaml"
            sh "sed -i 's|{ns}|${QANS}|g' k8s/k8s.yaml"
            sh "sed -i 's|{app_name}|${pipelineParams.appName}|g' k8s/k8s.yaml"
            sh "sed -i 's|{env}|qa|g' k8s/k8s.yaml"
            sh "sed -i 's|{nodename}|qa|g' k8s/k8s.yaml"
	    sh "sed -i 's|{platform}|apps|g' k8s/k8s.yaml"
            sh "cat k8s/k8s.yaml"
            //sh "sed -i 's|ACCOUNT|${ACCOUNT}|g' k8s/service.yaml"
            //sh "sed -i 's|ENVIRONMENT|dev|g' k8s/dev/*.yaml"
            // sh "sed -i 's|BUILD_NUMBER|01|g' k8s/dev/*.yaml"
            sh 'cat k8s/k8s.yaml'
            sh "kubectl config use-context arn:aws:eks:ap-south-1:233431242547:cluster/sdx"
            sh "kubectl apply -f k8s/k8s.yaml -n  ${QANS}"
            sh """
               kubectl patch deployment "${PROJECT}"  -p   "{\\"spec\\":{\\"template\\":{\\"metadata\\":{\\"annotations\\":{\\"date\\":\\"`date +'%s'`\\"}}}}}" -n "${QANS}" || true 
	    """
            //kubernetesDeploy configs: 'k8s/k8s.yaml', secretNamespace: "${env.QANS}", enableConfigSubstitution: false, kubeConfig: [path: ''], kubeconfigId: 'kubeconfig', secretName: '', ssh: [sshCredentialsId: '*', sshServer: ''], textCredentials: [certificateAuthorityData: '', clientCertificateData: '', clientKeyData: '', serverUrl: 'https://']

          }
        }

      }
	
	
// Prod-denver deployment.
      stage('Deploy to PRODDHMP') {

        when {
          anyOf {
            branch 'sdx_uat'
           //environment name: 'ARROVAL_ON_PRODDHMP_DEPLOY', value: 'yes'
          }
        }
        steps {
          script {
            env.IMAGE = "${USECRURL}/sdx-prod-denverhealth/${PROJECT}:${VERSION}"
            BuildPush("${env.IMAGE}")
            sh "sed -i 's|{image}|${env.IMAGE}|g' k8s/k8s.yaml"
            sh "sed -i 's|{ECRURL}|${USECRURL}|g' k8s/k8s.yaml"
            sh "sed -i 's|{ns}|${PRODDHMPNS}|g' k8s/k8s.yaml"
            sh "sed -i 's|{app_name}|${pipelineParams.appName}|g' k8s/k8s.yaml"
            sh "sed -i 's|{env}|prod|g' k8s/k8s.yaml"
            sh "sed -i 's|{nodename}|prod|g' k8s/k8s.yaml"
	    sh "sed -i 's|{platform}|apps|g' k8s/k8s.yaml"
            sh "cat k8s/k8s.yaml"
            sh 'cat k8s/k8s.yaml'
            sh "kubectl config use-context arn:aws:eks:us-east-1:233431242547:cluster/sdx"
            sh "kubectl apply -f k8s/k8s.yaml -n  ${PRODDHMPNS}"
            sh """
	    kubectl patch deployment "${PROJECT}"  -p   "{\\"spec\\":{\\"template\\":{\\"metadata\\":{\\"annotations\\":{\\"date\\":\\"`date +'%s'`\\"}}}}}" -n "${PRODDHMPNS}" || true
	    """
          }
        }

      }


      //QA demo deployment.
      stage('Deploy to QA DEMO') {

        when {
          anyOf {
            branch 'qa-demo'
            environment name: 'ARROVAL_ON_QA_DEPLOY', value: 'yes'
          }
        }
        steps {
          script {
            env.IMAGE = "${ECRURL}/sdx-qa-demo/${PROJECT}:${VERSION}"
            BuildPush("${env.IMAGE}")
            sh "sed -i 's|{image}|${env.IMAGE}|g' k8s/k8s.yaml"
            sh "sed -i 's|{ECRURL}|${ECRURL}|g' k8s/k8s.yaml"
            sh "sed -i 's|{ns}|${QADEMONS}|g' k8s/k8s.yaml"
            sh "sed -i 's|{app_name}|${pipelineParams.appName}|g' k8s/k8s.yaml"
            sh "sed -i 's|{env}|qa-demo|g' k8s/k8s.yaml"
            sh "sed -i 's|{nodename}|dev|g' k8s/k8s.yaml"
            sh "cat k8s/k8s.yaml"
            sh 'cat k8s/k8s.yaml'
            sh "kubectl config use-context arn:aws:eks:ap-south-1:233431242547:cluster/sdx"
            sh "kubectl apply -f k8s/k8s.yaml -n  ${QADEMONS}"
            sh """
	    kubectl patch deployment "${jobconsolename}"  -p   "{\\"spec\\":{\\"template\\":{\\"metadata\\":{\\"annotations\\":{\\"date\\":\\"`date +'%s'`\\"}}}}}" -n "${QADEMONS}" || true
	    """
          }
        }

      }

      stage('Arroval for prod Deployment') {
        when {
          branch 'elassandra'
        }
        steps {
          script {
            env.ARROVAL_ON_PROD_DEPLOY = input message: 'User input required',
              parameters: [choice(name: 'Deployment on PROD Environment', choices: 'no\nyes', description: 'Choose "yes" if you want to deploy this build on PROD Environment')]
          }
        }
      }

      stage('Deploy to prod') {

        when {
          anyOf {
            branch 'elassandra';
            environment name: 'ARROVAL_ON_PROD_DEPLOY', value: 'yes'
          }
        }
        steps {
          script {
            env.IMAGE = "${USECRURL}/sdx-prod/${PROJECT}:${VERSION}"

            BuildPush("${env.IMAGE}")
            //            sh "sed -i 's|{image}|${env.IMAGE}|g' k8s/k8s.yaml"
            //		 sh "sed -i 's|{ECRURL}|${ECRURL}|g' k8s/k8s.yaml"
            //		 sh "sed -i 's|{ns}|${PRODNS}|g' k8s/k8s.yaml"
            //		 sh "sed -i 's|{app_name}|${pipelineParams.appName}|g' k8s/k8s.yaml"
            //		 sh "sed -i 's|{env}|prod|g' k8s/k8s.yaml"
            //               sh "sed -i 's|{nodename}|prod|g' k8s/k8s.yaml"
            //		 sh "cat k8s/k8s.yaml"

            //	sh 'cat k8s/k8s.yaml'
            //	sh "kubectl apply -f k8s/k8s.yaml -n  ${PRODNS}"
            //	sh """
            //	  kubectl set image deployment/"${jobconsolename}" -n "${PRODNS}" "${jobconsolename}"=${env.IMAGE}
            //	  kubectl patch deployment "${jobconsolename}"  -p   "{\\"spec\\":{\\"template\\":{\\"metadata\\":{\\"annotations\\":{\\"date\\":\\"`date +'%s'`\\"}}}}}" -n "${PRODNS}" || true
            //	"""

          }
        }

      }

      /*
		stage('Deploy to Prod') {
			
          when {
                anyOf { 
			branch 'master1'; branch 'prod1' 
					}
            }
                   steps {
                        script {
						      IMAGE = "${ECRURL}/sdx-prod/${PROJECT}:${VERSION}"
						      BuildPush("${QAECRREPO}")
                             sh "sed -i 's|{image}|${IMAGE}|g' k8s/k8s.yaml"
							 sh "sed -i 's|{ns}|${PRODNS}|g' k8s/k8s.yaml"
							 sh "sed -i 's|{app_name}|${pipelineParams.appName}|g' k8s/k8s.yaml"
							 sh "sed -i 's|{env}|prod|g' k8s/k8s.yaml"
							 sh "cat k8s/k8s.yaml"
                             //sh "sed -i 's|ACCOUNT|${ACCOUNT}|g' k8s/service.yaml"
                             //sh "sed -i 's|ENVIRONMENT|prod|g' k8s/prod/*.yaml"
                             // sh "sed -i 's|BUILD_NUMBER|01|g' k8s/prod/*.yaml"
			kubernetesDeploy configs: 'k8s/k8s.yaml', secretNamespace: "${env.PRODNS}", enableConfigSubstitution: true, kubeConfig: [path: ''], kubeconfigId: 'kubeconfig', secretName: '', ssh: [sshCredentialsId: '*', sshServer: ''], textCredentials: [certificateAuthorityData: '', clientCertificateData: '', clientKeyData: '', serverUrl: 'https://']
                       
				 }
				}
		 
				} 
				
				*/

      stage('Static Code Analysis') {
        failFast true
        parallel {
          stage('SonarQube Analysis & Quality Gate') {
            steps {
              // sonarQubeScan("${env.JOB_NAME}".tokenize('/')[0], pipelineParams.BUILD_TOOL, pipelineParams.NODE_VERSION)
              // sh "mvn sonar:sonar -Dsonar.projectKey=${env.JOB_NAME} -Dsonar.host.url=${env.SONAR_URL} -Dsonar.login=${env.SONAR_TOKEN} -Dsonar.scm.disabled=true"
              //  qualityGate()
              echo "Sonar scan disabled to upgrade the plugin"
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
        stage('Release and publish artifact') {
            when {
                // check if branch is master
                branch 'develop'
            }
            steps {
                // create the release version then create a tage with it , then push to nexus releases the released jar
                script {
                    def mvnHome = tool 'Maven3' //
                    if (currentBuild.result == null || currentBuild.result == 'SUCCESS') {
                        def v = getReleaseVersion()
                        releasedVersion = v;
                        if (v) {
                            echo "Building version ${v} – so released version is ${releasedVersion}"
                        }
                        // jenkins user credentials ID which is transparent to the user and password change
                        //sshagent(['0000000-3b5a-454e-a8e6-c6b6114d36000']) {
                            sh "git tag -f v${v}"
                            //sh "git push -f –tags"
			    //sh "git push -f –tags HEAD:${env.BRANCH_NAME}"
                        //}
                        sh "'${mvnHome}/bin/mvn' -Dmaven.test.skip=true  versions:set  -DgenerateBackupPoms=false -DnewVersion=${v}"
                        //sh "'${mvnHome}/bin/mvn' -Dmaven.test.skip=true clean deploy"
			 echo "Update pom file"
			//sshagent(credentials: ["sivisoft-ssh-keys"]) {
			       sh ''' 
			          git config --global user.name 'devops@sivisoft.com'
                                  git config --global user.email devops@sivisoft.com
				  git add pom.xml
			        ''' 
			    	sh "git commit -m "Updated to version ${v}""
				sh "git push origin ${env.BRANCH_NAME}"
			//}  

                    } else {
                        error "Release is not possible. as build is not successful"
                    }
                }
            }
        } /*
		
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
        //sh "mvn --batch-mode release:update-versions -DdevelopmentVersion=${VERSION}"

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
        //sleep(10)

        deleteDir()
      }
    }
  }
}

/*

def getTimeStamp(){
    return sh (script: "date +'%Y%m%d%H%M%S%N' | sed 's/[0-9][0-9][0-9][0-9][0-9][0-9]\$//g'", returnStdout: true);
}

def getEnvVar(String paramName){
    return sh (script: "grep '${paramName}' env_vars/project.properties|cut -d'=' -f2", returnStdout: true).trim();
}
*/
def getDevVersion() {
  def gitCommit = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
  def versionNumber;
  if (gitCommit == null) {
    versionNumber = env.BUILD_NUMBER;
  } else {
    versionNumber = gitCommit.take(8);
  }

  print 'build  versions…'
  print versionNumber
  return versionNumber.replace("-SNAPSHOT", ".${versionNumber}")

}

def getReleaseVersion() {
  def pom = readMavenPom file: 'pom.xml'
  def gitCommit = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
  def versionNumber = env.BUILD_NUMBER;
  if (gitCommit == null) {
    versionNumber = env.BUILD_NUMBER;
  } else {
    versionNumber = gitCommit.take(8);
  }
  return pom.version.replace("-SNAPSHOT", ".${versionNumber}")
}
