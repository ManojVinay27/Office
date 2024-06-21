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
    String role

    SonarqubeUtil(String sonarToken, String mainURL = "https://sonar-dev.regeneron.regn.com") {
        util = new Util()
        main_url = mainURL
        sonar_token = sonarToken

        // Using Secret to decode sonar_token
        sonar_token = new Secret(sonar_token).getPlainText()
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
        util.logMessage("responseCode: ${responseCode}")

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

    void createProject(String project, String customProjectKey = "") {
        if (project == "") {
            throw new Exception("[Error]: Project name is empty")
        }

        if (customProjectKey == "") {
            def jobs = this.steps.env.BUILD_URL.split("/job/")
            util.logMessage("jobs: ${jobs}")

            if (jobs.size() < 3) {
                util.logMessage("Please create the job in a folder")
                throw new Exception("[Error]: Please create the job in a folder")
            }

            ritGroupName = jobs[0].split("/")[-1] + "_" + jobs[1] + "_" + project
            ritProjectName = ritGroupName
        } else {
            ritProjectName = customProjectKey
        }

        String query = "name=${project}&project=${ritProjectName}"
        util.logMessage("createProject query: ${query}")

        HttpURLConnection response = createHttpConnection("api/projects/create", query, "POST")

        if (response.responseCode == 200) {
            util.logMessage("Response: ${response.getContent()}")
            util.logMessage("Project created successfully")
        }

        if (response.responseCode == 400) {
            InputStream errorStream = response.errorStream
            String errorString = errorStream.text
            util.logMessage("Response: ${errorString}")
            util.logMessage("Project already exists")
        }
    }

    void createProjectLink(String projectKey, String linkName, String linkUrl) {
        String query = "projectKey=${projectKey}&name=${linkName}&url=${linkUrl}"
        util.logMessage("createProjectLink query: ${query}")

        HttpURLConnection response = createHttpConnection("api/project_links/create", query, "POST")

        if (response.responseCode == 200) {
            util.logMessage("Project link created successfully")
        } else {
            InputStream errorStream = response.errorStream
            String errorString = errorStream.text
            util.logMessage("Response: ${errorString}")
            util.logMessage("Failed to create Project link")
        }
    }

    void createTemplate(String templateName) {
        String query = "name=${templateName}"
        util.logMessage("createTemplate query: ${query}")

        HttpURLConnection response = createHttpConnection("api/permissions/create_template", query, "POST")

        if (response.responseCode == 200) {
            util.logMessage("Response: ${response.getContent()}")
            util.logMessage("Permission Template created successfully")
        }

        if (response.responseCode == 400) {
            InputStream errorStream = response.errorStream
            String errorString = errorStream.text
            util.logMessage("Response: ${errorString}")
            util.logMessage("Permission Template already exists")
        }
    }

    void createGroup(String permission, String environment = "dev", String projectName = "") {
        if (projectName == "") {
            util.logMessage("project is empty")
            ritGroupName = "rit_s_${environment}_${ritProjectName}_${permission}"
        } else {
            util.logMessage("project is not empty. In else block")
            ritGroupName = "rit_s_${environment}_${projectName}_${permission}"
        }

        String query = "name=${ritGroupName}"
        HttpURLConnection response = createHttpConnection("api/user_groups/create", query, "POST")

        util.logMessage("createGroup query: ${query}")

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

        return response
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

    void refreshApplication(String application) {
        String query = "application=${application}"
        HttpURLConnection response = createHttpConnection("api/applications/refresh", query, "POST")

        if (response.responseCode != 204) {
            throw new Exception("[Error]: Cannot refresh application")
        }
    }

    void searchTemplate(String templateName) {
        String query = "q=${templateName}"
        HttpURLConnection response = createHttpConnection("api/permissions/search_templates", query, "GET")

        if (response.responseCode == 200) {
            def responseBody = response.getInputStream().getText()
            def responseJson = util.jsonSlurper(responseBody)
            def permissionTemplatesId = responseJson.permissionTemplates[0].id
            util.logMessage("permissionTemplatesId: ${permissionTemplatesId}")
            return permissionTemplatesId
        } else {
            throw new Exception("[Error]: Cannot fetch permissionTemplatesId")
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
        }

        if (response.responseCode == 400) {
            InputStream errorStream = response.errorStream
            String errorString = errorStream.text
            util.logMessage("Response: ${errorString}")
            util.logMessage("Permission Template ${templateName} applied to project ${projectName} already exists")
        }
    }

    void addGroupToTemplate(String templateName, String userType = "dev", String groupName = "") {
        groupName = groupName ?: ritGroupName
        List permissions = ["user", "codeviewer", "issueadmin", "securityhotspotadmin", "scan", "user"]

        if (userType == "admin") {
            permissions = ["user", "admin"]
        }

        permissions.each { permission ->
            String query = "groupName=${groupName}&permission=${permission}&templateName=${templateName}"
            util.logMessage("addGroupToTemplate query: ${query}")

            HttpURLConnection response = createHttpConnection("api/permissions/add_group_to_template", query, "POST")

            if (response.responseCode == 200) {
                util.logMessage("Response: ${response.getContent()}")
                util.logMessage("Permission ${permission} added to template ${templateName} for group ${groupName} successfully")
            }

            if (response.responseCode == 400) {
                InputStream errorStream = response.errorStream
                String errorString = errorStream.text
                util.logMessage("Response: ${errorString}")
                util.logMessage("Permission ${permission} added to template ${templateName} for group ${groupName} already exists")
            }
        }
    }

    void addProjectToApplication(String applicationName, String projectName) {
        String query = "application=${applicationName}&project=${projectName}"
        util.logMessage("addProjectToApplication query: ${query}")

        HttpURLConnection response = createHttpConnection("api/applications/add_project", query, "POST")

        if (response.responseCode == 204) {
            util.logMessage("Response: ${response.getContent()}")
            util.logMessage("Project ${projectName} added to Application ${applicationName} successfully")
        } else {
            throw new Exception("[Error]: Cannot add project to application")
        }
    }

    List<String> getUserLogin(String users) {
        def userList = users.tokenize(",")
        List<String> userLoginIds = []

        userList.each { user ->
            String query = "q=${user.trim()}"
            HttpURLConnection response = createHttpConnection("api/users/search", query, "GET")

            if (response.responseCode == 200) {
                def responseBody = response.getInputStream().getText()
                def responseJson = util.jsonSlurper(responseBody)
                def userLoginId = responseJson.users[0].login
                util.logMessage("userLoginId: ${userLoginId}")
                userLoginIds.add(userLoginId)
            } else {
                throw new Exception("[Error]: Cannot fetch user login")
            }
        }

        return userLoginIds
    }
}
