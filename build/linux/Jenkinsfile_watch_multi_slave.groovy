
/* https://stackoverflow.com/questions/37025175/pipeline-pass-parameters-to-downstream-jobs
 * https://stackoverflow.com/questions/37594635/why-an-each-loop-in-a-jenkinsfile-stops-at-first-iteration
 *
 * TODO'S FOR OUR SLAVE JOB:
 * ------------------------------------------------------------------------
 * we'll need need to figure out how to build from the monolithic directory
 * need to figure out passing values to this pipeline from master pipeline
 *
 */

pipeline {

    environment {
        HAB_NOCOLORING = true
        //HAB_BLDR_URL = ''
        HAB_BLDR_URL = ''
        HAB_ORIGIN = 'ktraiste'
        BUILD_USER_ID = ''
        //The AUTH_TOKEN is the Habitat Builder auth token of user storing packages in origin
        HAB_AUTH_TOKEN = 'Test_Habitat_Jenkins_Cred'
	GIT_REMOTE_URL = "${params.BLD_PRMS.GIT_URL}"
	GIT_REMOTE_KEY = "${params.BLD_PRMS.GIT_KEY}"
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

        stage('clean directory')
            steps {
                deleteDir()
            }
        }

        stage('scm') {
            steps {
                //credentials ID should be ssh key configured to access SCM
		//this slave should build from master
                git url: "${env.GIT_REMOTE_URL}", credentialsId: "${GIT_REMOTE_KEY}"// , branch: "${env.BRANCH_NAME}"
            }
        }

	//need to understand how each team does their per-pkg builds

        stage('build') {
            steps {
                habitat task: 'build', directory: '.', origin: env.HAB_ORIGIN
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
		    env.PKG_NAME = env.HAB_ORIGIN + '/' + env.TMP_PKG_NAME
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
		 sh """
		    #!/bin/bash

		    new_pkg=${workspace}/results/${env.PKG_FILE_NAME}
		    me=\$(whoami)
		    hab pkg export docker \$new_pkg

                    """

                 script {
		    DOCKER_OUTPUT = sh(
		    script: "timeout --preserve-status 5 docker run ${HAB_ORIGIN}/${env.DOCKER_IMG_NAME}",
		    returnStdout: true
		    ).trim()
		 }

		 //need to figure out how we're going to call tests per package
	           
		 sh """
		    #!/bin/bash
		    
                    echo "CALLING TEST SCRIPTS SON"

                    echo "${DOCKER_OUTPUT}" | grep 'Hello, World!'

                    exit_code=\$?

                    #remove our image and all stopped containers
                    docker image rm -f ${HAB_ORIGIN}/${env.DOCKER_IMG_NAME}
                    docker rm $(docker ps -a -q)

		    if [[ \$exit_code -ne 0 ]]; then
			    echo "TEST SUITE FAILED!!"
			    echo "WE NEED TO STOP THE PROMOTION OF THIS PKG!"
			    return 3
	            fi

		    """
		}
        }

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

                    rm -rf ${workspace}build/last_build.txt
		    mv /tmp/last_build.txt${env.BUILD_NUMBER} ${workspace}/build/last_build.txt
		    git add -A
		    git commit -m "Jenkins updated build number automagically"
		    git tag -a \$pkg_version -m "Tagged by Jenkins" 

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

        stage('upload') {
            steps {
		   sh """
		   #!/bin/bash
		   cd ${workspace}
		   rm -rf results
		   mv /tmp/results_"${env.BUILD_NUMBER}" results
		   
		   """
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

	stage('notify of successful build') {
	  steps {
            sh ("""
                  #!/bin/bash

		  jenkins_url=\$(echo ${RUN_DISPLAY_URL} | sed 's/http:\\/\\/unconfigured-jenkins-location/https:\\/\\//' | sed 's/display/console/' | sed 's/redirect//' | sed 's/.\\{1\\}\$//')
		  
		  ruby build/teabot.rb -s \$jenkins_url
		  """)
	   }
        }
    }
    post {
        failure {
            sh("""
                  #!/bin/bash

		  jenkins_url=\$(echo ${RUN_DISPLAY_URL} | sed 's/http:\\/\\/unconfigured-jenkins-location/https:\\/\\//' | sed 's/display/console/' | sed 's/redirect//' | sed 's/.\\{1\\}\$//')
		  
		  ruby build/teabot.rb -f \$jenkins_url
		  """)

        }
	aborted {
	    sh("""
                  #!/bin/bash

                  #no message if this behavior is expected
		  if [[ ${HAB_CHECK_CODE} -eq 5 ]]; then
			  exit 0
		  fi

		  jenkins_url=\$(echo ${RUN_DISPLAY_URL} | sed 's/http:\\/\\/unconfigured-jenkins-location/https:\\/\\//' | sed 's/display/console/' | sed 's/redirect//' | sed 's/.\\{1\\}\$//')
		  
		  ruby build/teabot.rb -a \$jenkins_url
		  """)
	}
    }
}
