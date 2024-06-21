//must be called from an agent that supports Docker
def call(org, name, tag, dir, pushCredId) {
	/*
   dockerImage = docker.build("$org/$name:t$tag", "$dir")
    withDockerRegistry(registry: [credentialsId: "$pushCredId"]) { 
       dockerImage.push()
       dockerImage.push("${env.BRANCH_NAME}_${env.BUILD_NUMBER}")
        //    dockerImage.push("latest")
    }
    */
    	
    def image = docker.build("$org/$name")
			
			withCredentials([usernamePassword(credentialsId: 'docker-credentials', passwordVariable: 'C_PASS', usernameVariable: 'C_USER')]) {
                         //creds = "\nUser: ${C_USER}\nPassword: ${C_PASS}\n"
				sh "docker login -u ${C_USER} -p ${C_PASS}"
				image.push("${env.BRANCH_NAME}_${env.BUILD_NUMBER}")
                            image.push("latest")
                          }
    
}

def call() {
     	
                  echo "testing"	
	      //cleanup current user docker credentials
        sh 'rm  ~/.dockercfg || true'
        sh 'rm ~/.docker/config.json || true'
			// login to ECR - for now it seems that that the ECR Jenkins plugin is not performing the login as expected. I hope it will in the future.
                   // sh("eval \$(aws ecr get-login --no-include-email --region us-east-1| sed 's|https://||')")
	//sh("eval \$(aws ecr get-login --no-include-email --region ap-south-1| sed 's|https://||')")
	            //sh("eval \$(aws ecr get-login --no-include-email --region us-east-1)")
	           // sh '$'+"(aws ecr get-login --region us-east-1 --no-include-email| sed 's|https://||')"
	//sh("docker login --username AWS --password $(aws ecr get-login-password --region ap-south-1) 233431242547.dkr.ecr.ap-south-1.amazonaws.com")
	// sh 'aws ecr get-login-password --region ap-south-1 \| \$(docker login --username AWS --password-stdin 233431242547.dkr.ecr.ap-south-1.amazonaws.com)'
	sh """#!/bin/bash
             echo \$(aws ecr get-authorization-token --region ap-south-1 --output text --query 'authorizationData[].authorizationToken' | base64 -d | cut -d: -f2) | docker login -u AWS 233431242547.dkr.ecr.ap-south-1.amazonaws.com --password-stdin
	     echo \$(aws ecr get-authorization-token --region us-east-1 --output text --query 'authorizationData[].authorizationToken' | base64 -d | cut -d: -f2) | docker login -u AWS 233431242547.dkr.ecr.us-east-1.amazonaws.com --password-stdin
           """
                    // Push the Docker image to ECR
                    //docker.withRegistry(env.ECRURL, env.ECRCRED)
	//withAWS(role: 'jenkins_role'){
	           //docker.withRegistry('https://233431242547.dkr.ecr.us-east-1.amazonaws.com', 'ecr:us-east-1:ecr-credentials') 
	def image = docker.build(ECRREPO)
	
	        //docker.withRegistry(ECRURL, ECRCRED) 
	        // withDockerRegistry([ credentialsId:ECRCRED , url:ECRURL ]) 
		//{ 
                        //creds = "\nUser: ${C_USER}\nPassword: ${C_PASS}\n
	//image.push("${env.BRANCH_NAME}_${env.BUILD_NUMBER}")
       //                 image.push("latest")

      // sh """#!/bin/bash
	//    echo \$(aws ecr get-authorization-token --region us-east-1 --output text --query 'authorizationData[].authorizationToken' | base64 -d | cut -d: -f2) | docker login -u AWS 729980925064.dkr.ecr.us-east-1.amazonaws.com --password-stdin
	image.push("${env.VERSION}")
		//}
                 // }
    
}


def call(repourl) {
     	
                  echo "$repourl"	
	      //cleanup current user docker credentials
        sh 'rm  ~/.dockercfg || true'
        sh 'rm ~/.docker/config.json || true'
	//sh 'docker rmi $(docker images | grep "^<none>" | awk "{print $3}")'
			// login to ECR - for now it seems that that the ECR Jenkins plugin is not performing the login as expected. I hope it will in the future.
                   // sh("eval \$(aws ecr get-login --no-include-email --region us-east-1| sed 's|https://||')")
	//sh("eval \$(aws ecr get-login --no-include-email --region ap-south-1| sed 's|https://||')")
	//sh '$'+"(aws ecr get-login --region ap-south-1 --no-include-email| sed 's|https://||')"
	//sh("docker login --username AWS --password $(aws ecr get-login-password --region ap-south-1) 233431242547.dkr.ecr.ap-south-1.amazonaws.com")
	sh """#!/bin/bash
             echo \$(aws ecr get-authorization-token --region ap-south-1 --output text --query 'authorizationData[].authorizationToken' | base64 -d | cut -d: -f2) | docker login -u AWS 233431242547.dkr.ecr.ap-south-1.amazonaws.com --password-stdin
	     echo \$(aws ecr get-authorization-token --region us-east-1 --output text --query 'authorizationData[].authorizationToken' | base64 -d | cut -d: -f2) | docker login -u AWS 233431242547.dkr.ecr.us-east-1.amazonaws.com --password-stdin
	   """   
	
	
	            //sh("eval \$(aws ecr get-login --no-include-email --region us-east-1)")
                    // Push the Docker image to ECR
                    //docker.withRegistry(env.ECRURL, env.ECRCRED)
	//withAWS(role: 'jenkins_role'){
	           //docker.withRegistry('https://233431242547.dkr.ecr.us-east-1.amazonaws.com', 'ecr:us-east-1:ecr-credentials') 
	//def image = docker.build("$repourl")
	def image = docker.build("$repourl", "--no-cache --network=host -f Dockerfile .")
	//  echo "$repourl"
	        //docker.withRegistry(ECRURL, ECRCRED) 
	        // withDockerRegistry([ credentialsId:ECRCRED , url:ECRURL ]) 
		//{ 
                        //creds = "\nUser: ${C_USER}\nPassword: ${C_PASS}\n
	//image.push("${env.BRANCH_NAME}_${env.BUILD_NUMBER}")
       //  image.push("latest")
	image.push()
	
		//}
                 // }
    
}

def call(repourl,region) {
     	
                  echo "$repourl"	
	echo "$region"
	      //cleanup current user docker credentials
        sh 'rm  ~/.dockercfg || true'
        sh 'rm ~/.docker/config.json || true'
	//sh 'docker rmi $(docker images | grep "^<none>" | awk "{print $3}")'
			// login to ECR - for now it seems that that the ECR Jenkins plugin is not performing the login as expected. I hope it will in the future.
                   // sh("eval \$(aws ecr get-login --no-include-email --region us-east-1| sed 's|https://||')")
	//sh("eval \$(aws ecr get-login --no-include-email --region ap-south-1| sed 's|https://||')")
	//sh("docker login --username AWS --password \$(aws ecr get-login-password --region ap-south-1) 233431242547.dkr.ecr.ap-south-1.amazonaws.com")
	sh """#!/bin/bash
             echo \$(aws ecr get-authorization-token --region ap-south-1 --output text --query 'authorizationData[].authorizationToken' | base64 -d | cut -d: -f2) | docker login -u AWS 233431242547.dkr.ecr.ap-south-1.amazonaws.com --password-stdin
	     echo \$(aws ecr get-authorization-token --region us-east-1 --output text --query 'authorizationData[].authorizationToken' | base64 -d | cut -d: -f2) | docker login -u AWS 233431242547.dkr.ecr.us-east-1.amazonaws.com --password-stdin
           """

	//sh '$'+"(aws ecr get-login --region ap-south-1 --no-include-email| sed 's|https://||')"
	
	            //sh("eval \$(aws ecr get-login --no-include-email --region us-east-1)")
                    // Push the Docker image to ECR
                    //docker.withRegistry(env.ECRURL, env.ECRCRED)
	//withAWS(role: 'jenkins_role'){
	           //docker.withRegistry('https://233431242547.dkr.ecr.us-east-1.amazonaws.com', 'ecr:us-east-1:ecr-credentials') 
	//def image = docker.build("--build-arg region=$region $repourl")
	 echo "$repourl --- $region"
	def image = docker.build("$repourl", "--no-cache --network=host --build-arg region=$region -f Dockerfile .")
	 
	        //docker.withRegistry(ECRURL, ECRCRED) 
	        // withDockerRegistry([ credentialsId:ECRCRED , url:ECRURL ]) 
		//{ 
                        //creds = "\nUser: ${C_USER}\nPassword: ${C_PASS}\n
	//image.push("${env.BRANCH_NAME}_${env.BUILD_NUMBER}")
        // image.push("latest")
	image.push("${env.VERSION}")
		//}
                 // }
    
}
