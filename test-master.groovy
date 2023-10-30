import groovy.json.JsonSlurperClassic

pipeline {
  agent any
  
    environment {
        DOCKER_HOST= "unix:///run/podman/podman.sock"
        TESTCONTAINERS_RYUK_DISABLED = "true"
        PWD = pwd()
                    
            // Set the Started by and Module Name variables
            moduleName = "${params.Module}"
            deploymentEnv = "${params.Env}"
            branch = "${params.Branch}"
            jdkLevel = "${params.JDKLevel}"
            sonarQubeOnly = "${params.SonarQubeOnly}"
            skipTests = "${params.SkipTests}"
            enableCheckMarxScan = "${params.ScanCheckMarx}"
            testReportPath = "${WORKSPACE}/${moduleName}/target/surefire-reports"
    }
     
    stages {
        stage('Initialize') {
            steps {
                script{
                    deleteDir()
                    
                    def startedBy = 'Unknown'
                    for (cause in currentBuild.rawBuild.getCauses()) {
                        if (cause instanceof hudson.model.Cause.UserIdCause) {
                            startedBy = cause.userName
                            break
                        }
                    }
                    currentBuild.setDescription("Started by: ${startedBy}, \n Module Name: ${moduleName}, \n Environment: ${deploymentEnv}, \n Branch: ${branch} \n JDK: ${jdkLevel} \n SonarQubeOnly : ${SonarQubeOnly}")
                    def gitRepo = '-'
                    if(moduleName == 'sc-configuration'){
                        gitRepo = 'select-config-backend'
                    } else if(moduleName == 'sc-solutionGuide'){
                        gitRepo = 'select-config-backend-solution-Guide'
                    } else if(moduleName == 'sc-productCatalogue'){
                        gitRepo = 'select-config-backend-productCatalogue'
                    } else if(moduleName == 'sc-asu-detection'){
                        gitRepo = 'select-config-asu-detection'
                    } else if(moduleName == 'sc-hierarchy'){
                        gitRepo = 'Select-Config-Backend-Hierarchy'
                    } else if(moduleName == 'sc-administration'){
                        gitRepo = 'Select-Config-Backend-Administration'
                    } else if(moduleName == 'sc-productSelector'){
                        gitRepo = 'Select-Config-Backend-ProductSelector'
                    }
                    
                    //define scm connection for polling
                    def gitUrl = "git@github.schneider-electric.com:se-bb-admin/${gitRepo}.git"
                    git branch: "${params.Branch}", url: gitUrl
                }
            }
        }
        stage('Build') {
          steps {
            script {
              echo "Building Package"
                def jdkOld = 'JDK8'
                if(jdkLevel == '8'){
                    withEnv(["PATH+EXTRA=/usr/sbin:/usr/bin:/sbin:/bin","JAVA_HOME=${tool jdkOld}"]) {
                    //withEnv(["PATH+EXTRA=/usr/sbin:/usr/bin:/sbin:/bin","JAVA_HOME = '/app/jdk-1.8'"]) {
                        echo 'running with java 8'
                        sh 'JAVA_HOME=/app/jdk-1.8 /app/maven2/bin/mvn -f ${WORKSPACE}/pom.xml clean install -DskipTests'
                    }
                } else {
                    withEnv(['PATH+EXTRA=/usr/sbin:/usr/bin:/sbin:/bin']) {
                        sh 'mvn -f ${WORKSPACE}/pom.xml clean install -DskipTests'
                    }
                }
            }
          }
        }
        
        stage('Tests') {
            steps {
                script {
                    if(skipTests != "true"){
                      echo "Executing Tests"
                      def jdkOld = 'JDK8'
                      if(jdkLevel == '8'){
                            //withEnv(["PATH+EXTRA=/usr/sbin:/usr/bin:/sbin:/bin","JAVA_HOME=${tool jdkOld}"]) {
                            withEnv(["PATH+EXTRA=/usr/sbin:/usr/bin:/sbin:/bin","JAVA_HOME=/app/jdk-1.8"]) {
                                try {
                                    // Execute Maven test command
                                    sh '/app/maven2/bin/mvn -f ${WORKSPACE}/pom.xml test'
                                } catch (Exception e) {
                                    // Ignore the exception and continue with the pipeline
                                    echo "Maven test failed, but ignoring the failure."
                                }
                            }
                        } else {
                            withEnv(['PATH+EXTRA=/usr/sbin:/usr/bin:/sbin:/bin']) {
                                try {
                                    // Execute Maven test command
                                    sh 'mvn -f ${WORKSPACE}/pom.xml test'
                                } catch (Exception e) {
                                    throw e
                                    // Ignore the exception and continue with the pipeline
                                    // echo "Maven test failed, but ignoring the failure."
                                }
                            }
                        }
                    }
                }
            }
        }
        
        stage('SonarQube analysis'){
            steps{
                script{
                    if(skipTests != "true"){
                        withSonarQubeEnv(installationName: 'SONAR-LOCAL') {
                            withEnv(['PATH+EXTRA=/usr/sbin:/usr/bin:/sbin:/bin']) {
                                sh 'mvn -f ${WORKSPACE}/pom.xml org.sonarsource.scanner.maven:sonar-maven-plugin:3.7.0.1746:sonar'      
                            }
                        }
                    }
                }
            }    
        }
        
    stage('Checkmarks'){
        steps{
            script {
                if(enableCheckMarxScan == "true"){
                    try{
                        checkmarxASTScanner additionalOptions: '--sast-incremental --debug --project-tags SNCAPI', baseAuthUrl: '', branchName: "${branch}", checkmarxInstallation: 'checkmarks', credentialsId: 'Checkmarx Cred', projectName: "${moduleName}", serverUrl: 'https://eu.iam.checkmarx.net', tenantName: 'alpicloud', useOwnAdditionalOptions: true, useOwnServerCredentials: true
                    } catch (Exception e){
                        echo "CheckMarx scan failed. Check the logs for error."
                        throw e
                    }
                    echo "CheckMarx scan complete. See the results on : https://eu.iam.checkmarx.net"
                }
            }
        }
    }
        stage('Deployment') {
          steps {
            script {
                if(sonarQubeOnly == "false"){
                    version = readMavenPom().getVersion()
                    echo "${version}"
                    def Job = "-"
                    if(params.Module == "sc-configuration"){
                        Job = "CM"
                    } else if(params.Module == "sc-solutionGuide"){
                        Job = "SG"
                    } else if(params.Module == "sc-productCatalogue"){
                        Job = "PC"
                    } else if(params.Module == "sc-asu-detection"){
                        Job = "ASU"
                    } else if(params.Module == "sc-hierarchy"){
                        Job = "HM"
                    } else if(params.Module == "sc-administration"){
                        Job = "AM"
                    } else if(params.Module == "sc-productSelector"){
                        Job = "PS"
                    }
                    echo "building TEST-MASTER-Deployment-Manager with : \n Job : '${Job}' \n ModuleName : '${moduleName}' \n Version : '${version}' \n Env : '${deploymentEnv}'"
                    build job: 'TEST-MASTER-Deployment-Manager',
                      parameters: [
                        [$class: 'StringParameterValue', name: 'Job', value: Job],
                        [$class: 'StringParameterValue', name: 'ModuleName', value: moduleName],
                        [$class: 'StringParameterValue', name: 'Version', value: version],
                        [$class: 'StringParameterValue', name: 'Env', value: deploymentEnv]
                      ]
                }
            }
          }
        }
        stage('HTTPD DHealth') {
            steps {
                script {
                    if(sonarQubeOnly == "false"){
                        timeout(time: 60, unit: 'SECONDS') {
                            // Call the dhealth endpoint
                            def app = "-"
                            if(moduleName == "sc-solutionGuide"){
                                app = "selectionguides"
                            } else if (moduleName == "sc-configuration"){
                                app = "configurations"
                            } else if (moduleName == "sc-productCatalogue"){
                                app = "products"
                            } else if (moduleName == "sc-administration"){
                                app = "administration"
                            } else if (moduleName == "sc-hierarchy"){
                                app = "hierarchy"
                            }
                            
                            def environment = "-"
                            if(deploymentEnv == "ci"){
                                environment = "dev"
                            } else if (deploymentEnv == "ci_vnv"){
                                environment = "int"
                            } else if (deploymentEnv == "ci_vnv_poc"){
                                environment = "int"
                            } else if (deploymentEnv == "sqe"){
                                environment = "sqe"
                            }
                            echo "Checking DHealth for https://selectconfig-${environment}.schneider-electric.com/${app}/Dhealth"
                            def httpStatus = 0
                            withEnv(['PATH+EXTRA=/usr/sbin:/usr/bin:/sbin:/bin']){
                                def url = "https://selectconfig-${environment}.schneider-electric.com/${app}/Dhealth"
                                def curlOutput = sh(returnStdout: true, script: "curl -s -o /dev/null -w '%{http_code}' ${url}").trim()
                                def jsonResponse = sh(returnStdout: true, script: "curl -s ${url}").trim()
                                echo "Curl output: ${curlOutput}"
                                echo "Response JSON: ${jsonResponse}"
                                if (!curlOutput.equals("200")) {
                                    echo "Build failed because DHealth check returned ${curlOutput}."
                                    error("Build failed because DHealth check returned ${curlOutput}.")
                                }
                                if (jsonResponse.contains('DOWN')) {
                                    echo "Build failed because the DHealth has 1 or more components DOWN."
                                    error("Build failed because the DHealth has 1 or more components DOWN.")
                                }
                            }
                        }
                    }
                }
            }
        }
        
        stage('JMETER SANITY') {
            steps {
                script {
                    if(sonarQubeOnly == "false"){
                        def Job = "-"
                        if(params.Module == "sc-configuration"){
                            Job = "CM"
                        } else if(params.Module == "sc-solutionGuide"){
                            Job = "SG"
                        } else if(params.Module == "sc-productCatalogue"){
                            Job = "PC"
                        } else if(params.Module == "sc-asu-detection"){
                            Job = "ASU"
                        } else if(params.Module == "sc-hierarchy"){
                            Job = "HM"
                        } else if(params.Module == "sc-administration"){
                            Job = "AM"
                        } else if(params.Module == "sc-productSelector"){
                            Job = "PS"
                        }
                        
                        def ENVIRONMENT = "-"
                        if(deploymentEnv == "ci"){
                            ENVIRONMENT = "DEV"
                        } else if (deploymentEnv == "ci_vnv"){
                            ENVIRONMENT = "INT"
                        } else if (deploymentEnv == "ci_vnv_poc"){
                            ENVIRONMENT = "INT"
                        } else if (deploymentEnv == "sqe"){
                            ENVIRONMENT = "SQE"
                        }
                        def URL = "https://selectconfig-${deploymentEnv}.schneider-electric.com"
                        build job: 'MASTER-JMETER-SANITY',
                          parameters: [
                            [$class: 'StringParameterValue', name: 'ENVIRONMENT', value: ENVIRONMENT],
                            [$class: 'StringParameterValue', name: 'MODULE', value: Job],
                            [$class: 'StringParameterValue', name: 'STACK', value: "ELB"],
                            [$class: 'StringParameterValue', name: 'URL', value: URL]
                          ]
                    }
                }
            }
        }
    }
    
    post {
        always {
            script {
                def testReportPath = "${WORKSPACE}"
                echo "List of files in ${testReportPath}:"
                withEnv(['PATH+EXTRA=/usr/sbin:/usr/bin:/sbin:/bin']) {
                    sh "find ${testReportPath} -name 'TEST-*.xml'"      
                }
                junit testResults: "**/TEST-*.xml"
                plot name: "${moduleName} Test Results Trend", 
                    group: "${moduleName} Group",
                    datafile: [
                        file: "${moduleName}/target/surefire-reports/*.xml",
                        displayTable: true
                    ]
            }
        }
    }
}