package water.gradle.plugins
import org.gradle.api.*
import org.gradle.api.plugins.*
import groovy.xml.MarkupBuilder
import java.util.regex.Matcher
import java.util.regex.Pattern

apply plugin: 'manageClouds'

buildscript {
    repositories {
        mavenCentral()
        jcenter()
    }

    dependencies {
        compile gradleApi()
        compile localGroovy()
        classpath 'org.codehaus.groovy.modules.http-builder:http-builder:0.7.1'
    }
}
//
import groovyx.net.http.HTTPBuilder
import static groovyx.net.http.Method.GET
import static groovyx.net.http.ContentType.TEXT

class manageClouds implements Plugin<Project> {
    def eol = System.getProperty('line.separator')
    def jarName = 'h2o.jar'
    def jpsPidPattern = ~/\s*(\d+)\s+h2o.jar\s*/
    def createClouds = {project->
        def jar = [project.parent.buildDir.canonicalPath,jarName].join(System.getProperty('file.separator'))
        project.ant.exec(executable: 'java',dir: project.buildDir.canonicalPath,spawn: true){
            arg(line:"-Xmx2g -ea -jar ${jar} -name gradle-test -baseport 54321")
        }
    }

    def waitOnClouds = {project ->
        project.ant.exec(executable: 'jps', dir: project.buildDir.canonicalPath, outputproperty: 'cmdOut')
        project.ant.properties.cmdOut.split('\n').each{
            Matcher m = (it =~ jpsPidPattern)
            if ( m.matches() ){
                project.ext.set('cloudsPid', Integer.parseInt(m[0][1]))
            }
        }
        def http = new HTTPBuilder('http://localhost:54321')
        def cloudIsUp = false
        while ( !cloudIsUp ){
            http.request(GET, TEXT) {req->
                uri.path = "/"
                response.success = { resp, reader->
                    cloudIsUp = true
                    assert resp.status == 200
                }
                response.'404' = {resp ->
                }
            }
        }
        project.logger.warn " *** Cloud at http://localhost:54321 is up and running ***"
        project.logger.warn "PID: " + project.ext.cloudsPid.toString()

    }

    void apply(Project project) {
        project.task('manageClouds') << {
            createClouds(project)
            waitOnClouds(project)
        }

    }
}