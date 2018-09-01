
/* This file is intended to create a pipeline for developers to streamline their development, testing, and test deployment of their programs
 * This file should NEVER BE USED TO PUSH PRODUCTION CODE, as it lacks ChatOps functionality and production fail-safes
 *
 * v1.0
 */

pipeline {

    environment {
        HAB_NOCOLORING = true
        //HAB_BLDR_URL = ''
        HAB_BLDR_URL = 'http://8.8.8.8/'
        HAB_ORIGIN = 'ktraiste'
        BUILD_USER_ID = ''
        //The AUTH_TOKEN is the Habitat Builder auth token of user storing packages in origin
        HAB_AUTH_TOKEN = 'Test_Habitat_Jenkins_Cred'
	GIT_REMOTE_URL = 'git@github.com:SQL-PDP/habitat-main.git'
	GIT_REMOTE_KEY = '93612e9a-7628-466b-9991-b252d112e2e5' 
    }

    agent {
        node {
            label 'win'
        }
    }

    stages {
	stage('clean workspace') {
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

        stage('build') {
            steps {
                habitat task: 'build', directory: '.', origin: "${env.HAB_ORIGIN}"
            }
        }

	stage('instantiate_variables'){
            steps {
		script {
		    env.PKG_FILE_NAME = powershell(
		        script: "\$a = Get-ChildItem -Path ${workspace}\\results\\*.hart | Sort-Object LastAccessTime -Descending | Select-Object -First 1 ; echo \$a.name",
			returnStdout: true
			).trim()
		    println(env.PKG_FILE_NAME)
		   }

                //Trying to get away from needing this manipulation
		//ktraiste/udagent/10.22/20180808154433
		script {
		    env.HAB_PKG_NAME = powershell(
		    script: """
		    \$my_string="${env.PKG_FILE_NAME}" ; \$my_string=\$my_string.replace('-x86_64-windows.hart','') ; \$my_string=\$my_string.replace('-','/') ; echo \$my_string
		    """, 
		    returnStdout: true
		    ).trim()
		    println(env.HAB_PKG_NAME)
                }

		script {
		    env.DOCKER_IMG_NAME = powershell(
		    script: "echo ${env.PKG_FILE_NAME} | %{ \$_.split('-')[2]}",
		    returnStdout: true
		    ).trim()
		    println(env.DOCKER_IMG_NAME)
		    }

		script {
		    env.PKG_NAME = env.HAB_ORIGIN + '/' + env.PKG_FILE_NAME
		    }
   	      }
	}

	/* TESTS GO HERE
	 *
	 * AT LEAST THEY WOULD
	 * WE'RE HAVING TROUBLE WITH THIS FOR NOW
	 * COMMENTING OUT FOR TESTING TO PROGRESS
	 *
	 * we're going to export the hab package into a docker container and run with a test suite
	 * as long as the package passes the tests, we want to promote
	 * if tests fail, we want to leave package as unstable

	stage('test') {
	     steps {
		 powershell """
		    \$new_pkg="${workspace}/results/${env.PKG_FILE_NAME}"
		    echo \$new_pkg
                    #you should really use the habitat plugin to do this bit, but its demonstrated in the Linux pipelines
		    hab pkg export docker \$new_pkg

                    """

                 script {
		    powershell(
		    //format for the docker image becomes ktraiste/
		    script: "docker run -d ${HAB_ORIGIN}/${env.DOCKER_IMG_NAME} ",
		    )
		 }
	           
		 powershell """
                    echo "CALLING TEST SCRIPTS SON"

                    ./your_tests_here.sh

                    exit_code=\$?

                    #remove our image and all stopped containers
                    docker image rm -f ${HAB_ORIGIN}/${env.DOCKER_IMG_NAME}
                    docker ps -a -q | % {
			    docker rm \$_
	            }

		    If ( \$exit_code -ne 0 ) {
			    echo "TEST SUITE FAILED!!"
			    echo "WE NEED TO STOP THE PROMOTION OF THIS PKG!"
			    exit 5
                    }
		    """
		}
        }
	*/

	//we want to promote before merge to master before push in DEV ONLY
	//QA/PROD should pull from MASTER instead of FEATURE BRANCH
	
       /* 
        stage('merge to master') {
          steps {
		 sh( """
                    #!/bin/bash

                    cd ${workspace}
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

                    git config --global user.email "jenkins@automate.com"
                    git config --global user.name "\$(hostname)"

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
                    sh 'echo ssh -i ${GITHUB_KEY} -l git -o StrictHostKeyChecking=no \\"\\$@\\" > /tmp/run_ssh.sh'
                    sh 'chmod +x /tmp/run_ssh.sh'
                    withEnv(['GIT_SSH=/tmp/run_ssh.sh']) {
                        sh 'git push origin master --tags'
		    }
		    sh 'rm -rf /tmp/run_ssh.sh'
		}
            }
	}
	*/

        stage('upload') {
            steps {
		   /*
		   sh """
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
                    habitat task: 'promote', channel: 'development', authToken: "${env.HAB_AUTH_TOKEN}", artifact: "${env.HAB_PKG_NAME}" ,bldrUrl: "${env.HAB_BLDR_URL}" 
                }
            }
        }

    }
}
