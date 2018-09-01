
/* This file is intended to create a pipeline for developers to streamline their development, testing, and test deployment of their programs
 * This file should NEVER BE USED TO PUSH PRODUCTION CODE, as it lacks ChatOps functionality and production fail-safes
 *
 * v1.0
 */

pipeline {

    environment {
        HAB_NOCOLORING = true
        //HAB_BLDR_URL = ''
        HAB_BLDR_URL = ''
        HAB_ORIGIN = 'ktraister'
	BUILD_USER_ID = 'hab'
        //The AUTH_TOKEN is the Habitat Builder auth token of user storing packages in origin
        HAB_AUTH_TOKEN = 'Test_Habitat_Jenkins_Cred'
        GIT_REMOTE_URL = 'git@github.com:ktraister/Habitat-sample-package-with-Jenkinsfile.git'
        GIT_REMOTE_KEY = 'Habitat_Hello_World_Key' 
        SONAR_HOME = tool name: 'Test-SonarQube', type: 'hudson.plugins.sonar.SonarRunnerInstallation'
    }

    agent {
        node {
            label 'lnx'
            //wrap([$class: 'BuildUser']) {
                //def user = env.BUILD_USER_ID
            //}
        }
    }

    stages {

        stage('clean directory') {
            steps {
                deleteDir()
            }
        }

        stage('scm') {
            steps {
                //credentials ID should be ssh key configured to access SCM
                git url: "${env.GIT_REMOTE_URL}", credentialsId: "${GIT_REMOTE_KEY}" , branch: "${env.BRANCH_NAME}"
            }
        }

        stage('SonarQube analysis') {
	    steps {
	        // requires SonarQube Scanner 2.8+
	        withSonarQubeEnv('Test-SonarQube') {
		     sh "${env.SONAR_HOME}/bin/sonar-scanner -X -Dsonar.host.url=${SONAR_HOST_URL} -Dsonar.projectKey='comcv.JenkinsPipelineStaticCodeAnalysis' -Dsonar.projectBaseDir=${workspace} -D sonar.sources=. -Dsonar.login=${SONAR_AUTH_TOKEN} -Dsonar.projectName='Hello_World' -D sonar.projectVersion='1'"
	        }
	    }
	}

        stage('build') {
            steps {
                habitat task: 'build', directory: '.', origin: "${env.HAB_ORIGIN}"
            }
        }

	stage('docker export') {
            steps {
                habitat task: 'export', format: "docker", lastBuildFile: "${workspace}/results/last_build.env", bldrUrl: "${env.HAB_BLDR_URL}"
		/*
		    script {
		    env.PKG_FILE_NAME = sh(
		    script:  "ls -t ${workspace}/results/ | grep hart | head -n 1",
		    returnStdout: true
		    ).trim()
		    }
   
		    sh """
                    #!/bin/bash

                    new_pkg=${workspace}/results/${env.PKG_FILE_NAME}
                    me=\$(whoami)
                    
		    docker ps

                    hab pkg export docker \$new_pkg

                    """
		 */
            }
        }

	stage('instantiate_variable'){
            steps {
		script {
		    env.PKG_FILE_NAME = sh(
		    script:  "ls -t ${workspace}/results/ | grep hart | head -n 1",
		    returnStdout: true
		    ).trim()
		   }

		script {
		    env.TMP_PKG_NAME = sh(
		    script: "echo ${env.PKG_FILE_NAME} | sed 's/-x86_64-linux.hart//' | cut -d '-' -f 2-20 | sed 's/\\(.*\\)-/\\1\\//' | sed 's/\\(.*\\)-/\\1\\//'",
		    returnStdout: true
		    ).trim()
                }

		script {
		    env.DOCKER_IMG_NAME = sh(
		    script: "echo ${env.TMP_PKG_NAME} | cut -d '/' -f 1",
		    returnStdout: true
		    ).trim()
		    }

		script {
		    env.PKG_NAME = "${env.HAB_ORIGIN}" + '/' + "${env.TMP_PKG_NAME}"
		    }
   	      }
	}

	/* TESTS GO HERE
	 *
	 * we're going to export the hab package into a docker container and run with a test suite
	 * as long as the package passes the tests, we want to promote
	 * if tests fail, we want to leave package as unstable
	 */

	stage('test') {
	     steps {
                 script {
		    DOCKER_OUTPUT = sh(
		    script: "timeout -t 5 docker run ${env.HAB_ORIGIN}/${env.DOCKER_IMG_NAME} && exit 0",
		    returnStdout: true
		    ).trim()
		 }
	           
		 sh ( """
		    #!/bin/bash
		    
                    echo "CALLING TEST SCRIPTS SON"

                    echo "${DOCKER_OUTPUT}" | grep 'Hello, World!'

                    exit_code=\$?

                    docker image rm -f "${env.HAB_ORIGIN}"/"${env.DOCKER_IMG_NAME}"
                    docker rm \$(docker ps -a -q -f status=exited)

		    if [[ \$exit_code -ne 0 ]]; then
			    echo "TEST SUITE FAILED!!"
			    echo "WE NEED TO STOP THE PROMOTION OF THIS PKG!"
			    return 5
	            fi

		    """
		    )
		}
        }

	//we want to promote before merge to master before push in DEV ONLY
	//QA/PROD should pull from MASTER instead of FEATURE BRANCH
	
	/*
        stage('merge to master') {
          steps {
		 sh( """
                    #!/bin/bash

                    cd "${workspace}"
		    mv results /tmp/results_"${env.BUILD_NUMBER}"
		    rm -rf * 
		    rm -rf .git

		    """)

		 //clone the repo cleanly to merge to master
                 git url: "${env.GIT_REMOTE_URL}" , credentialsId: "${env.GIT_REMOTE_KEY}" , branch: "${env.BRANCH_NAME}"

		 //sh 'git tag -a tagName -m "Your tag comment"'
		 //we could also do a git pull under here instead of a git merge
		 //talk to Adam/Sai about preferred method

		 sh( """
		    #!/bin/bash

		    HOME="/home/\$(whoami)"
		    echo '\$HOME: ' \$HOME

                    git config --global user.email "jenkins@automate.com"
                    git config --global user.name "Jenkins_Bot"

                    git checkout master
                    git merge "${env.BRANCH_NAME}"

                    version=\$(git tag -l | sed 's/v//' | sort -V | tail -n 1)
		    myver=\$(echo \$version)
		    if [[ -z \$myver ]]; then
			pkg_version='0.1'
		    else
			pkg_min_version=\$(echo \$myver | cut -d '.' -f 2)
			pkg_maj_version=\$(echo \$myver | cut -d '.' -f 1)
			if [[ -z \$pkg_maj_version ]] || [[ \$pkg_maj_version -eq '' ]]; then
			    pkg_maj_version=0
			    pkg_min_version=\$(expr \$pkg_min_version + 1)
			    pkg_version=\$pkg_maj_version.\$pkg_min_version
			elif [[ \$pkg_min_version -eq 9 ]]; then
			    pkg_maj_version=\$(expr \$pkg_maj_version + 1)
			    pkg_min_version=0
		            pkg_version=\$pkg_maj_version.\$pkg_min_version
			else
			    pkg_min_version=\$(expr \$pkg_min_version + 1)
			    pkg_version=\$pkg_maj_version.\$pkg_min_version
			fi
  		    fi
		    
		    pkg_version="v\$pkg_version"
		    git tag -a \$pkg_version -m "Tagged by Jenkins" 

		    #git commit -am "Jenkins merged branch into master automagically"

		    """)

                withCredentials([sshUserPrivateKey(credentialsId: "${env.GIT_REMOTE_KEY}", keyFileVariable: 'GITHUB_KEY')]) {
                    sh 'echo ssh -i "${GITHUB_KEY}" -l git -o StrictHostKeyChecking=no \\"\\$@\\" > /tmp/run_ssh.sh'
                    sh 'chmod +x /tmp/run_ssh.sh'
                    withEnv(['GIT_SSH=/tmp/run_ssh.sh']) {
                        sh 'git push origin master --tags'
		    }
		    sh 'rm -rf /tmp/run_ssh.sh'
		}
            }
	}

	stage('get prod approval') {
            steps{
                  sh ("""
                  #!/bin/bash

                  git_commit=\$(git log | head -n 1 | cut -d ' ' -f 2)
                  git_url=\$(echo ${GIT_REMOTE_URL} | cut -d '@' -f 2 | sed 's/:/\\//' | sed 's/.git/\\/commit\\//')
                  git_flowbot_cmd=\$git_url\$git_commit

                  jenkins_url=\$(echo ${RUN_DISPLAY_URL} | sed 's/http:\\/\\/unconfigured-jenkins-location/https:\\/\\//' | sed 's/display/console/' | sed 's/redirect//' | sed 's/.\\{1\\}\$//')

                  ruby build/teabot.rb -n \$git_flowbot_cmd \$jenkins_url
                  """)

                  timeout (time: 1, unit: 'HOURS') {
                          input 'Proceed to upload?'
                     }
               }
        }
	*/

        stage('upload') {
            steps {
		   /*
		   sh """
		   #!/bin/bash
		   mv /tmp/results_"${env.BUILD_NUMBER}" results
		   
		   """
		   */
                   withCredentials([string(credentialsId: "${env.HAB_AUTH_TOKEN}" , variable: 'HAB_AUTH_TOKEN')]) {
                    habitat task: 'upload', authToken: "${env.HAB_AUTH_TOKEN}", lastBuildFile: "${workspace}/results/last_build.env", bldrUrl: "${env.HAB_BLDR_URL}"
                }

            }
        }

        stage('promote') {
          steps {
            withCredentials([string(credentialsId: "${env.HAB_AUTH_TOKEN}" , variable: 'HAB_AUTH_TOKEN')]) {
                    habitat task: 'promote', channel: 'development', authToken: "${env.HAB_AUTH_TOKEN}", artifact: "${env.PKG_NAME}" ,bldrUrl: "${env.HAB_BLDR_URL}" 
                }
            }
        }
    }
}
