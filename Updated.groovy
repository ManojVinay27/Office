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

    SonarqubeUtil(String sonarToken, String mainURL="https://sonar-dev.regeneron.regn.com") {
        util = new Util()
        main_url = mainURL
        sonar_token = sonarToken

        // Token retrieval not working in sharedlib
        // withCredentials ([string(credentialsId: sonarToken, variable: 'SONAR_TOKEN')]) {
        // sonar_token= new Secret(SONAR_TOKEN).getPlainText()
        // }
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
        util.logMessage("responseCode ${responseCode}")

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
            util.logMessage("jobs: ${jobs}")

            if (jobs.size() < 3) {
                util.logMessage("Please create the job in a folder")
                throw new Exception("[Error]: Please create the job in a folder")
            }

            ritGroupName = jobs[0].split("/")[-1] + "_" + jobs[1] + "_" + project
        } else {
            ritProjectName = customProjectKey
        }

        String query = "name=${URLEncoder.encode(project, "UTF-8")}&project=${URLEncoder.encode(ritProjectName, "UTF-8")}"
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
        String query = "project=${URLEncoder.encode(projectKey, "UTF-8")}&name=${URLEncoder.encode(linkName, "UTF-8")}&url=${URLEncoder.encode(linkUrl, "UTF-8")}"
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

    // Other methods remain unchanged...
}
