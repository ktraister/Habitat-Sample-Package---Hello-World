# Habitat Sample Packages With Jenkinsfiles
Chef Habitat is the next generation of CI tools, offering lightning-fast updates and built-in dependancy management. 
This sample repo outlines how to set up a habitat package and build, test, and deploy the package using Jenkins with declarative pipelines.
Contained herein is the script being packaged, the plan.sh to outline how to build the package, hooks to be used in the package, sample Jenkinsfiles, and a custom ruby integration for Flowdock.

#Jenkins Job setup

PREREQUISITES:
-------------
 - Your repository should be "habitized", including your run hook and plan.sh
 - a Jenkinsfile following the template of the one in this repo should exist in your project root
 - you should choose a branch for the Jenkins job. DONT USE MA$TER
 - {.pub,.sig.key} should be set up on the slave to run the job to push to the correct origin


On the Jenkins homepage, select the "New Item" field. Name your project, and select "Multibranch Pipeline" for your project type

On the setup page for your job, configure the following items:
-------------------------------------------------------------
 - point "Branch Sources" to git, and your repo and credentials should be configured
    - use an SSH key configured to read/write on your repo
    - behaviors should be set up to "Discover Branches" and exclude master from builds, include *
 - "Build Configuration" should be set to Jenkinsfile, and point to the name of the file in your repo 

In your Jenkinsfile, configure the following items:
---------------------------------------------------
 - HAB_AUTH_TOKEN should be configured with the token of the user in the Hab Origin
    - This can be set up as a secret in Jenkins, type "Secret Text"
 - GIT_REMOTE_URL will need to be configured to be the repo you want to work on
 - GIT_REMOTE_KEY will be set up with ID of the read/write key

