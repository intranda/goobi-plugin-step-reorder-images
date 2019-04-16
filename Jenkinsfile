pipeline {

  agent {
    docker {
      image 'maven:3-jdk-8'
      args '-v $HOME/.m2:/var/maven/.m2:z -u 1000 -ti -e _JAVA_OPTIONS=-Duser.home=/var/maven -e MAVEN_CONFIG=/var/maven/.m2'
    }
  }

  options {
    buildDiscarder logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '15', daysToKeepStr: '90', numToKeepStr: '')
  }

  triggers {
    upstream 'goobi-workflow/goobi/master'
  }
  
  stages {
    stage('prepare') {
      steps {
        sh 'git clean -fdx'
      }
    }

    stage('build') {
      steps {
        sh 'mvn -f goobi-plugin-step-reorder-images/pom.xml install'
        recordIssues enabledForFailure: true, aggregatingResults: true, tools: [java(), javaDoc()]
      }
    }
  }
  
  post {
    success {
      archiveArtifacts artifacts: '*/module-*/target/*.jar, */plugin_*.xml', fingerprint: true
    }
    changed {
      emailext(
        subject: '${DEFAULT_SUBJECT}',
        body: '${DEFAULT_CONTENT}',
        recipientProviders: [requestor(),culprits()],
        attachLog: true
      )
    }
  }
}

/* vim: set ts=2 sw=2 tw=120 et :*/