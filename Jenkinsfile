pipeline {
	agent any

	options {
		buildDiscarder(logRotator(artifactDaysToKeepStr: '60', artifactNumToKeepStr: '50', daysToKeepStr: '60', numToKeepStr: '50'))
	}

	environment {
		releaseVersion = decodeAndLoadVersionFromCommit()
		releaseType = decodeTypeFromCommit()
	}

	stages {

		stage("Build") {
			steps {
				sh "./gradlew cleanTest build --continue --stacktrace"
			}
			post {
				always {
					junit testResults: "**/TEST-*.xml"
				}
			}
		}

		stage("Publish Artifacts") {
			when {
				not { environment name: "releaseType", value: "none" }
			}
			environment {
				ORG_GRADLE_PROJECT_releaseVersion = "$releaseVersion"
				ORG_GRADLE_PROJECT_releaseType = "$releaseType"
			}
			steps {
				publishWithGradleArtifactory()
			}
			post {
				success {
					gitTagRelease()
				}
			}
		}

		stage("Publish Tags") {
			steps {
				gitPushTags('jenkins-generated-ssh-key')
			}
		}
	}
}