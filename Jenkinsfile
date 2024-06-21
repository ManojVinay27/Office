@Library('DevOpsLib@T230-8228')
import com.regeneron.radit.devops.sonarqube.SonarqubeUtil

SonarqubeUtil sonarutil

def groupName = ""
def managerName = "derek.yamadang"
def LDAP_OU = "OU-OktaAuthz, OU=Application Groups, OU=Regeneron Groups, DC=regeneron, DC=regn, DC=com"

// [TODO] Create a map for SonarUrl and in the params just show Dev/Prod/Test/DCE as the choice options

pipeline {
    agent any

    parameters {
        string(name: "ApplicationName", defaultValue: "", description: 'Name of the Application to which the Project needs to be linked.')
        string(name: "ControllerName", defaultValue: "", description: 'Name of the controller in Jenkins')
        string(name: "SubFolderName", defaultValue: "", description: 'Name of the sub folder of controller in Jenkins')
        string(name: "ProjectName", defaultValue: "", description: 'Name of the project to be created in SonarQube.')
        choice(name: "SonarUrl", choices: ['https://sonar-dev.regeneron.regn.com', 'https://sonar.regeneron.regn.com'], description: 'Environment of the SonarQube.')
        string(name: "Users", defaultValue: "", description: 'Comma separated list of users to be added')
        choice(name: "Permission", choices: ['dev', 'admin'], description: 'Permission to be added to the group.')
        string(name: "ProjectLinkName", defaultValue: "", description: 'Name of the Project link')
        string(name: "ProjectLinkURL", defaultValue: "", description: 'URL of the Project link')
    }

    stages {
        stage("Authenticate Sonarqube") {
            steps {
                script {
                    def credentials_id = ""
                    if (params.SonarUrl == 'https://sonar-dev.regeneron.regn.com') {
                        credentials_id = "sonarqube-auth-token-dev"
                    } else if (params.SonarUrl == 'https://sonar.regeneron.regn.com') {
                        credentials_id = "sonarqube-auth-token-prod"
                    }

                    withCredentials([string(credentialsId: credentials_id, variable: 'SONAR_TOKEN')]) {
                        sonarutil = new SonarqubeUtil(SONAR_TOKEN, params.SonarUrl)
                    }
                }
            }
        }

        stage("Create project") {
            when {
                expression { return params.ProjectName != '' }
            }
            steps {
                script {
                    if (params.ControllerName != '' && params.SubFolderName != '') {
                        // This is the use case coming from ServiceNow
                        sonarutil.createProject(params.ProjectName, customProjectKey="${params.ControllerName}_${params.SubFolderName}")
                    } else {
                        sonarutil.createProject(params.ProjectName)
                    }

                    // Create the Project link
                    sonarutil.createProjectLink(params.ProjectName, params.ProjectLinkName, params.ProjectLinkURL)
                }
            }
        }

        stage("Create Application") {
            when { expression { return params.ApplicationName != '' } }
            steps {
                script {
                    sonarutil.createApplication(params.ApplicationName)
                }
            }
        }

        stage("Add to Application") {
            when { expression { return params.ApplicationName != '' && params.ProjectName != '' } }
            steps {
                script {
                    sonarutil.addProjectToApplication(params.ApplicationName, params.ProjectName)
                }
            }
        }

        stage("Manage Template and Groups") {
            when { expression { return params.Users != '' && params.Permission != '' } }
            steps {
                script {
                    def response = sonarutil.createGroup(permission=params.Permission, environment="dev")
                    // def userLoginIds = sonarutil.getUserLogin(params.Users.tokenize(","))
                    // sonarutil.mapGroupToApplication(params.ApplicationName, groupName, params.Permission)
                    sonarutil.createTemplate(params.ProjectName)
                    sonarutil.addGroupToTemplate(templateName=params.ProjectName, userType=params.Permission)
                    sonarutil.addGroupToTemplate(templateName=params.ProjectName, userType="admin", groupName="Group_okta_App_Sonar_administrators")
                    sonarutil.applyTemplate(sonarutil.ritProjectName, params.ProjectName)
                }
            }
        }

        stage("Manage groups in LDAP") {
            when { expression { return params.Users != '' && groupName != '' } }
            steps {
                script {
                    build job: "/ldap-management/create-nested-group-in-ou/",
                    parameters: [
                        string(name: 'BASE_RAD_OU', value: LDAP_OU),
                        string(name: 'GROUP_NAME', value: sonarutil.ritGroupName),
                        string(name: 'USER_LIST', value: sonarutil.ritGroupName),
                        string(name: 'PARENT_GROUP_NAME', value: "Group_Okta_App_sonarqube_Prod"),
                        string(name: 'DESCRIPTION', value: "Parent group for Sonarqube Application"),
                        string(name: 'MANAGER', value: managerName)
                    ],
                    wait: true,
                    propagate: true

                    build job: "/ldap-management"
                }
            }
        }
    }
}
