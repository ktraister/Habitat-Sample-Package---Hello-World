
/* https://stackoverflow.com/questions/37025175/pipeline-pass-parameters-to-downstream-jobs
 * https://stackoverflow.com/questions/37594635/why-an-each-loop-in-a-jenkinsfile-stops-at-first-iteration
 *
 * This pipeline is intended to make an API call to determine which (if any) versions of pkgs have been updated
 * for each updated pkg is found, we make a call to build it to:
 * ----------------------------------------------------------
 *  another job
 *    > pass the name of the pkg to the pipeline
 *    --> as long as the GIT_URL is the same, we can pass that too
 *    --> need to figure out how to build from monolithic git dir
 *    > parallelism
 *    --> we can definitely parallelize this 
 *
 *===========================================================
 *
 * At any rate, we'll need need to figure out how to build from the monolithic directory
 *
 *
 */

pipeline {

    environment {
        HAB_NOCOLORING = true
        //HAB_BLDR_URL = ''
        HAB_BLDR_URL = ''
	HAB_TEAM_ORIGIN = 'ktraiste'
        HAB_ORIGIN = 'deployment'
        BUILD_USER_ID = ''
        //The AUTH_TOKEN is the Habitat Builder auth token of user storing packages in origin
        HAB_AUTH_TOKEN = 'Test_Habitat_Jenkins_Cred'
	GIT_REMOTE_URL = 'git@githubcom:ktraiste/Habitat-sample-package-with-Jenkinsfile.git'
	GIT_REMOTE_KEY = 'Habitat_Hello_World_Key' 
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
                git url: "${env.GIT_REMOTE_URL}", credentialsId: "${env.GIT_REMOTE_KEY}"//, branch: "${env.BRANCH_NAME}"
            }
        }

        //with this stage, we're going to check the origin for a list of pkgs
        stage('pkg check') {
	    steps {
		script {
			env.HAB_CHECK = sh (
			script: """
			    #!/bin/bash

                            pkgs_tobuild=()

			    git checkout master

			    while read entry; do
				hab_pkg=\$(echo \$entry | cut -d ':' -f 1)
				git_test_var=\$(echo \$entry  | cut -d ':' -f 2)
				last_build=\$(curl http://8.8.8.8:9636/v1/depot/pkgs/ktraiste/\$hab_pkg | sed -e 's/[{}]/''/g' | awk -v k="text" '{n=split(\$0,a,","); for (i=1; i<=n; i++) print a[i]}' | grep release | head -n 1 | cut -d ':' -f 2 | sed 's/"//g')
				##compare last_build var to last_build.txt in git dir
				##if they are the same, we exit
				##if the branch is not master, we'll build it anyway. Need to checkout master

				if [[ \$git_test_var != \$last_build ]]; then
				    #I want the actual slave running the job to update the last_build.txt file
				    #sed -i '' "s/\$hab_pkg:\$git_test_var/\$hab_pkg:\$last_build/" last_build.txt
				    pkgs_tobuild+=(\$hab_pkg:\$last_build)
				fi

			    done < "${workspace}/build/last_build.txt"

			    #git checkout ${env.BRANCH_NAME}

			    echo \${pkgs_tobuild[@]}

			  """,
			  returnStdout:true
			  )

                    TEST_HAB_CK = HAB_CHECK.replaceAll("\\s","")

	      if ( TEST_HAB_CK.empty ) {
		   currentBuild.result = 'ABORTED'
                   error('quitting without a new origin build to proceed with')
		  }

	      env.HAB_PKG = HAB_CHECK.tokenize()
	      }
	   }
	}

        /*
	 *   THIS IS WHERE WE NEED TO MAKE A DECISION
	 */

	stage('call all build jobs') {
	  steps {
            script {
              for ( HABITAT_PACKAGE in "${env.HAB_PKG}") {
	        build ( job: 'Test - Multi Pkg Build Slave', 
	                parameters: [[$class: 'BLD_PRMS', 
		        name: 'GIT_URL', value: "${env.GIT_REMOTE_URL}",
		        name: 'GIT_KEY', value: "${env.GIT_REMOTE_KEY}",
		        name: 'PKG', value: HABITAT_PACKAGE
		        ]] )
	        }
	      }
	   }
	}

	stage('notify of successful build') {
	  steps {
            sh ("""
                  #!/bin/bash

		  jenkins_url=\$(echo "${RUN_DISPLAY_URL}" | sed 's/http:\\/\\/unconfigured-jenkins-location/https:\\/\\//' | sed 's/display/console/' | sed 's/redirect//' | sed 's/.\\{1\\}\$//')
		  
		  ruby build/teabot.rb -s \$jenkins_url
		  """)
	   }
        }
    }
    post {
        failure {
            sh("""
                  #!/bin/bash

		  jenkins_url=\$(echo "${RUN_DISPLAY_URL}" | sed 's/http:\\/\\/unconfigured-jenkins-location/https:\\/\\//' | sed 's/display/console/' | sed 's/redirect//' | sed 's/.\\{1\\}\$//')
		  
		  ruby build/teabot.rb -f \$jenkins_url
		  """)

        }
	aborted {
	    sh("""
                  #!/bin/bash

                  #no message if this behavior is expected
		  echo "env.HAB_CHECK:" "${env.HAB_CHECK}"
		  if [[ \$(echo "${env.HAB_CHECK}" | tr -d ' \\t\\n\\r\\f') == '' ]]; then
			  exit 0
		  fi

		  jenkins_url=\$(echo "${RUN_DISPLAY_URL}" | sed 's/http:\\/\\/unconfigured-jenkins-location/https:\\/\\//' | sed 's/display/console/' | sed 's/redirect//' | sed 's/.\\{1\\}\$//')
		  
		  ruby build/teabot.rb -a \$jenkins_url
		  """)
	}
    }
}
