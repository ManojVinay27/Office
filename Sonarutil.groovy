package com.regeneron.radit.devops.sonarqube

import hudson.util.Secret
import java.io.Serializable
import groovy.json.JsonSlurperClassic
import com.regeneron.radit.devops.Util

class SonarqubeUtil implements Serializable {

    protected def steps = (new Util()).getPipelineSteps()
    String sonar_token
    String main_url
    Util util
    String project_name
    String ritGroupName
    String ritProjectName

    SonarqubeUtil(String sonarToken, String mainURL="https://sonar-dev.regeneron.regn.com") {
        util = new Util()
        main_url = mainURL
        sonar_token = new Secret(sonarToken).getPlainText()
        util.logMessage("sonar_token:${sonar_token}")
    }

    private HttpURLConnection createHttpConnection(String apiEndPoint, String payload, String method) {
        def jsonSlurper = new JsonSlurperClassic()
        def raw = sonar_token + ':'
        def bauth = 'Basic ' + javax.xml.bind.DatatypeConverter.printBase64Binary(raw.getBytes())
        def conn = new URL("${main_url}/${apiEndPoint}?${payload}").openConnection() as HttpURLConnection
        conn.setRequestMethod(method)
        conn.setRequestProperty("Authorization", bauth)
        conn.connect()
        def responseCode = conn.responseCode
        util.logMessage("responseCode = ${responseCode}")
        if (responseCode == 400 || (responseCode >= 200 && responseCode < 300)) {
            return conn
        } else if (responseCode == 401) {
            throw new Exception("[Error]: Unauthorized")
        } else {
            InputStream errorStream = conn.errorStream
            String errorString = errorStream.text
            throw new Exception("[Error]: ${responseCode}: ${errorString}")
        }
    }

    void createProject(String project, String customProjectKey="") {
        if (project == "") {
            throw new Exception("[Error]: Project name is empty")
        }

        if (customProjectKey == "") {
            def jobs = this.steps.env.BUILD_URL.split("/job/")
            util.logMessage("jobs:${jobs}")
            if (jobs.size() < 3) {
                util.logMessage("Please create the job in a folder")
                throw new Exception("[Error]: Please create the job in a folder")
            }
            ritProjectName = jobs[0].split("/")[1] + "_" + jobs[1] + "_" + project
            // TODO: Write the logic to push to ServiceNow
        } else {
            ritProjectName = customProjectKey
        }

        String query = "name=${project}&project=${ritProjectName}"
        util.logMessage("createProject query: ${query}")
        HttpURLConnection response = createHttpConnection("api/projects/create", query, "POST")
        if (response.responseCode == 200) {
            util.logMessage("Response: ${response.getContent()}")
            util.logMessage("Project created successfully")
        } else if (response.responseCode == 400) {
            InputStream errorStream = response.errorStream
            String errorString = errorStream.text
            util.logMessage("Response: ${errorString}")
            util.logMessage("Project already exists")
        }
    }

    void createTemplate(String templateName) {
        String query = "name=${templateName}"
        util.logMessage("createTemplate query: ${query}")
        HttpURLConnection response = createHttpConnection("api/permissions/create_template", query, "POST")
        if (response.responseCode == 200) {
            util.logMessage("Response: ${response.getContent()}")
            util.logMessage("Permission Template created successfully")
        } else if (response.responseCode == 400) {
            InputStream errorStream = response.errorStream
            String errorString = errorStream.text
            util.logMessage("Response: ${errorString}")
            util.logMessage("Permission Template already exists")
        }
    }

    void createGroup(String permission, String environment="dev", String project_name="") {
        if (project_name == "") {
            util.logMessage("project is empty")
            ritGroupName = "rit_s_${environment}_${ritProjectName}_${permission}"
        } else {
            util.logMessage("project is not empty. In else block")
            ritGroupName = "rit_s_${environment}_${project_name}_${permission}"
        }
        String query = "name=${ritGroupName}"
        util.logMessage("createGroup query: ${query}")
        HttpURLConnection response = createHttpConnection("api/user_groups/create", query, "POST")
        if (response.responseCode == 200) {
            util.logMessage("Created group ${ritGroupName}")
        } else if (response.responseCode == 400) {
            InputStream errorStream = response.errorStream
            String errorString = errorStream.text
            util.logMessage("Response: ${errorString}")
            util.logMessage("Group ${ritGroupName} already exists")
        } else {
            throw new Exception("[Error]: Cannot create group")
        }
    }

    void createApplication(String applicationName, String visibility = "private") {
        String query = "name=${applicationName}&visibility=${visibility}&key=${applicationName}"
        HttpURLConnection response = createHttpConnection("api/applications/create", query, "POST")
        if (response.responseCode == 200) {
            util.logMessage("Application created successfully")
        } else if (response.responseCode == 400) {
            InputStream errorStream = response.errorStream
            String errorString = errorStream.text
            util.logMessage("Response: ${errorString}")
            util.logMessage("Application already exists")
        } else {
            throw new Exception("[Error]: Cannot create application")
        }
        refreshApplication(applicationName)
    }

    void searchTemplate(String templateName) {
        String query = "q=${templateName}"
        HttpURLConnection response = createHttpConnection("api/permissions/search_templates", query, "GET")
        if (response.responseCode == 200) {
            def responseBody = response.getInputStream().getText()
            def responseJson = util.jsonSlurper(responseBody)
            def permissionTemplatesId = responseJson.permissionTemplates[0].id
            util.logMessage("permissionTemplatesId:${permissionTemplatesId}")
            return permissionTemplatesId
        } else {
            throw new Exception("[Error]: Cannot fetch permission TemplatesId")
        }
    }

    void applyTemplate(String projectName, String templateName) {
        String templateId = searchTemplate(templateName)
        String query = "projectKey=${projectName}&templateId=${templateId}"
        util.logMessage("applyTemplate query: ${query}")
        HttpURLConnection response = createHttpConnection("api/permissions/apply_template", query, "POST")
        if (response.responseCode == 200) {
            util.logMessage("Response: ${response.getContent()}")
            util.logMessage("Permission Template ${templateName} applied to project ${projectName} successfully")
        } else if (response.responseCode == 400) {
            InputStream errorStream = response.errorStream
            String errorString = errorStream.text
            util.logMessage("Response: ${errorString}")
            util.logMessage("Permission Template ${templateName} applied to project ${projectName} already exists")
        }
    }

    void addGroupToTemplate(String templateName, String userType="dev", String groupName="") {
        groupName = groupName ?: ritGroupName
        List permissions = ["user", "codeviewer", "issueadmin", "securityhotspotadmin", "scan", "user"]
        if (userType == "admin") {
            permissions = ["user", "admin"]
        }
        permissions.each { permission ->
            String query = "groupName=${groupName}&permission=${permission}&templateName=${templateName}"
            util.logMessage("addGroupToTemplate query ${query}")
            HttpURLConnection response = createHttpConnection("api/permissions/add_group_to_template", query, "POST")
            if (response.responseCode == 200) {
                util.logMessage("Response: ${response.getContent()}")
                util.logMessage("Permission ${permission} added to template ${templateName} for group ${groupName} successfully")
            } else if (response.responseCode == 400) {
                InputStream errorStream = response.errorStream
                String errorString = errorStream.text
                util.logMessage("Response: ${errorString}")
            }
        }
    }

    void addProjectToApplication(String application, String project) {
        String query = "application=${application}&project=${project}"
        HttpURLConnection response = createHttpConnection("api/applications/add_project", query, "POST")
        if (response.responseCode == 200 || response.responseCode == 204) {
            util.logMessage("Project added successfully")
        } else if (response.responseCode == 400) {
            InputStream errorStream = response.errorStream
            String errorString = errorStream.text
            util.logMessage("Response: ${errorString}")
            util.logMessage("Project already added to application")
        } else {
            throw new Exception("[Error]: Cannot add project to application")
        }
        refreshApplication(application)
    }

    void refreshApplication(String application) {
        String query = "application=${application}"
        HttpURLConnection response = createHttpConnection("api/applications/refresh", query, "POST")
        if (response.responseCode != 204) {
            throw new Exception("[Error]: Cannot refresh application")
        }
    }

    void addUserToGroup(List<String> users, String groupName) {
        users.each { user ->
            String query = "login=${user}&name=${groupName}"
            HttpURLConnection response = createHttpConnection("api/user_groups/add_user", query, "POST")
            if (response.responseCode != 204) {
                throw new Exception("[Error]: Cannot add user to group")
            }
        }
    }

    void mapGroupToApplication(String applicationKey, String groupName, String permission) {
        String query = "projectKey=${applicationKey}&groupName=${groupName}&permission=${permission}&permission=user"
        util.logMessage("query: ${query}")
        HttpURLConnection response = createHttpConnection("api/permissions/add_group", query, "POST")
        if (response.responseCode == 204) {
            util.logMessage("Permission ${permission} added to group ${groupName} for application ${applicationKey}")
        } else if (response.responseCode == 400) {
            util.logMessage("Group ${groupName} already has permission ${permission} for application ${applicationKey}")
        } else {
            throw new Exception("[Error]: Cannot add group to application")
        }
    }

    String getUserLogin(List<String> users) {
        def userIdList = []
        def jsonSlurper = new JsonSlurperClassic()
        users.each { user ->
            String query = "q=${user}@regeneron.com"
            HttpURLConnection response = createHttpConnection("api/users/search", query, "GET")
            if (response.responseCode == 200) {
                def responseBody = response.getInputStream().getText()
                def responseJson = jsonSlurper.parseText(responseBody)
                def userLoginId = responseJson.users[0].login
                util.logMessage("User response login: ${userLoginId}")
                userIdList.add(userLoginId)
            } else {
                throw new Exception("[Error]: Cannot fetch application key")
            }
        }
        return userIdList
    }

    String getApplicationKey(String applicationName) {
        String query = "q=${applicationName}"
        HttpURLConnection response = createHttpConnection("api/applications/search", query, "GET")
        if (response.responseCode == 200) {
            def jsonSlurper = new JsonSlurperClassic()
            def jsonResponse = jsonSlurper.parse(response.inputStream)
            def application = jsonResponse.components.find { it.name == applicationName }
            return application ? application.key : null
        } else {
            throw new Exception("[Error]: Cannot fetch application key")
        }
    }

    String getApplicationGroup(String applicationName) {
        return "Group_okta_app_sonar_${applicationName}"
    }

    String getGroupName(String groupName) {
        String query = "q=${groupName}"
        HttpURLConnection response = createHttpConnection("api/user_groups/search", query, "GET")
        if (response.responseCode == 200) {
            def jsonSlurper = new JsonSlurperClassic()
            def jsonResponse = jsonSlurper.parse(response.inputStream)
            def group = jsonResponse.components.find { it.name == groupName }
            return group ? group.key : null
        } else {
            throw new Exception("[Error]: Cannot fetch application key")
        }
    }

    void checkAndSyncPermissions(String applicationGroup, String applicationKey) {
        List<String> appPermissions = getPermissions(applicationGroup)
        List<String> projectPermissions = getPermissions(applicationKey)
        appPermissions.each { permission ->
            if (applicationPermissions.contains(permission)) {
                setApplicationPermissions(applicationKey, applicationGroup, permission)
            }
        }
    }

    List<String> getPermissions(String entity) {
        // Placeholder method for getting permissions; needs implementation
        return []
    }

    void setApplicationPermissions(String applicationKey, String applicationGroup, String permission) {
        // Placeholder method for setting permissions; needs implementation
    }
}
