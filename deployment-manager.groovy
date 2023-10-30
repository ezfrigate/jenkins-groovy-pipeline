import groovy.json.JsonSlurperClassic
pipeline {
    agent any
    stages{
        stage('Deployment') {
          steps{
              script{
                currentBuild.setDescription("Job : ${params.Job}, \n Module Name: ${params.ModuleName}, \n Environment: ${params.Env}, \n Version : ${params.Version}")
                def version = params.Version
                def x_prod_cn_ip = "-"
                def x_prod_ip = "-"
                def x_ppr_ip = "-"
                def x_data2_ip = "-"
                def x_data1_ip = "-"
                if(params.Env == "sqe"){
                    x_prod_cn_ip = "10.194.159.204"
                    x_prod_ip = "10.194.159.203"
                    x_ppr_ip = "10.194.159.202"
                    x_data2_ip = "10.194.159.201"
                    x_data_ip = "10.194.159.200"
                } else if (params.Env == "ci_vnv" || params.Env == "ci_vnv_poc"){
                    x_prod_cn_ip = "10.194.156.41"
                    x_prod_ip = "10.194.156.41"
                    x_ppr_ip = "10.194.156.41"
                    x_data2_ip = "10.194.156.41"
                    x_data_ip = "10.194.156.41"
                } else if (params.Env == "ci"){
                    x_prod_cn_ip = "10.194.156.45"
                    x_prod_ip = "10.194.156.45"
                    x_ppr_ip = "10.194.156.45"
                    x_data2_ip = "10.194.156.45"
                    x_data_ip = "10.194.156.45"
                }
                echo 'var set'
                if (params.Job == "SG" || params.Job == "PC" || params.Job == "HM" || params.Job == "AM" || params.Job == "PS"){
                    //def threadCN = new Thread({
                        echo "Deploying ${params.ModuleName}-${version} on ${params.Env}_prod_cn"
                        build job: 'TEST-DEPLOY',
                          parameters: [
                            [$class: 'StringParameterValue', name: 'PROJECT_NAME', value: "${params.ModuleName}"],
                            [$class: 'StringParameterValue', name: 'VERSION', value: version],
                            [$class: 'StringParameterValue', name: 'ENV', value: "${params.Env}_prod2"],
                            [$class: 'StringParameterValue', name: 'MAVEN_REPOSITORY', value: 'local_repository'],
                            [$class: 'StringParameterValue', name: 'LIST_SSH_SERVER', value: "app@${x_prod_cn_ip}"],
                            [$class: 'StringParameterValue', name: 'INSTALLER_NAME', value: "install-${params.ModuleName}-${version}.tar"]
                          ] 
                    //})
                    //def threadPRD = new Thread({
                        echo "Deploying ${params.ModuleName}-${version} on ${params.Env}_prod"
                        build job: 'TEST-DEPLOY',
                          parameters: [
                            [$class: 'StringParameterValue', name: 'PROJECT_NAME', value: "${params.ModuleName}"],
                            [$class: 'StringParameterValue', name: 'VERSION', value: version],
                            [$class: 'StringParameterValue', name: 'ENV', value: "${params.Env}_prod"],
                            [$class: 'StringParameterValue', name: 'MAVEN_REPOSITORY', value: 'local_repository'],
                            [$class: 'StringParameterValue', name: 'LIST_SSH_SERVER', value: "app@${x_prod_ip}"],
                            [$class: 'StringParameterValue', name: 'INSTALLER_NAME', value: "install-${params.ModuleName}-${version}.tar"]
                          ] 
                    //})
 
                    //def threadPPR = new Thread({
                        echo "Deploying ${params.ModuleName}-${version} on ${params.Env}_ppr"  
                        build job: 'TEST-DEPLOY',
                          parameters: [
                            [$class: 'StringParameterValue', name: 'PROJECT_NAME', value: "${params.ModuleName}"],
                            [$class: 'StringParameterValue', name: 'VERSION', value: version],
                            [$class: 'StringParameterValue', name: 'ENV', value: "${params.Env}_ppr"],
                            [$class: 'StringParameterValue', name: 'MAVEN_REPOSITORY', value: 'local_repository'],
                            [$class: 'StringParameterValue', name: 'LIST_SSH_SERVER', value: "app@${x_ppr_ip}"],
                            [$class: 'StringParameterValue', name: 'INSTALLER_NAME', value: "install-${params.ModuleName}-${version}.tar"]
                          ] 
                    //})
  
                    //def threadDATA = new Thread({
                        if(x_data_ip != x_data2_ip){
                            echo "Deploying ${params.ModuleName}-${version} on ${params.Env}_data2"  
                            build job: 'TEST-DEPLOY',
                              parameters: [
                                [$class: 'StringParameterValue', name: 'PROJECT_NAME', value: "${params.ModuleName}"],
                                [$class: 'StringParameterValue', name: 'VERSION', value: version],
                                [$class: 'StringParameterValue', name: 'ENV', value: "${params.Env}"],
                                [$class: 'StringParameterValue', name: 'MAVEN_REPOSITORY', value: 'local_repository'],
                                [$class: 'StringParameterValue', name: 'LIST_SSH_SERVER', value: "app@${x_data2_ip}"],
                                [$class: 'StringParameterValue', name: 'INSTALLER_NAME', value: "install-${params.ModuleName}-${version}.tar"]
                              ]
                        }
                    //})
                    //threadCN.start()
                    //threadPRD.start()
                    //threadPPR.start()
                    //threadDATA.start()
                    echo "Deploying ${params.ModuleName}-${version} on ${params.Env}_data1"
                    build job: 'TEST-DEPLOY',
                      parameters: [
                        [$class: 'StringParameterValue', name: 'PROJECT_NAME', value: "${params.ModuleName}"],
                        [$class: 'StringParameterValue', name: 'VERSION', value: version],
                        [$class: 'StringParameterValue', name: 'ENV', value: "${params.Env}"],
                        [$class: 'StringParameterValue', name: 'LIST_SSH_SERVER', value: "app@${x_data_ip}"],
                        [$class: 'StringParameterValue', name: 'INSTALLER_NAME', value: "install-${params.ModuleName}-${version}.tar"]
                      ]
                    //threadCN.join()
                    //threadPRD.join()
                    //threadPPR.join()
                    //threadDATA.join()
                } else if(params.Job == "CM" || params.Job == "ASU"){
                    if(x_data_ip != x_data2_ip){
                        echo "Deploying ${params.ModuleName}-${version} on ${params.Env}_data2"  
                        build job: 'TEST-DEPLOY',
                          parameters: [
                            [$class: 'StringParameterValue', name: 'PROJECT_NAME', value: "${params.ModuleName}"],
                            [$class: 'StringParameterValue', name: 'VERSION', value: version],
                            [$class: 'StringParameterValue', name: 'ENV', value: "${params.Env}"],
                            [$class: 'StringParameterValue', name: 'LIST_SSH_SERVER', value: "app@${x_data2_ip}"],
                            [$class: 'StringParameterValue', name: 'INSTALLER_NAME', value: "install-${params.ModuleName}-${version}.tar"]
                          ]
                    }
                    echo "Deploying ${params.ModuleName}-${version} on ${params.Env}_data"
                    build job: 'TEST-DEPLOY',
                      parameters: [
                        [$class: 'StringParameterValue', name: 'PROJECT_NAME', value: "${params.ModuleName}"],
                        [$class: 'StringParameterValue', name: 'VERSION', value: version],
                        [$class: 'StringParameterValue', name: 'ENV', value: "${params.Env}"],
                        [$class: 'StringParameterValue', name: 'LIST_SSH_SERVER', value: "app@${x_data_ip}"],
                        [$class: 'StringParameterValue', name: 'INSTALLER_NAME', value: "install-${params.ModuleName}-${version}.tar"]
                      ]
                }
              }
          }
        }
    }
  }