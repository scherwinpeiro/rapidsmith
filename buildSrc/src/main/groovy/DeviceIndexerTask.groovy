import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

class DeviceIndexerTask extends DefaultTask {
	@InputDirectory
	def File inputDir

	@OutputDirectory
	def File outputDir

	@TaskAction
	def indexDevices() {
		project.delete(outputDir.listFiles())
		project.copy {
			from inputDir
			into outputDir
		}

		def families = inputDir.listFiles()

		def mainIndexFile = new File(outputDir,"familyIndex.txt")

		mainIndexFile.withPrintWriter { familyListWriter ->
			families.each { family ->
				familyListWriter.println(family.getName());
				def familyDir = new File(outputDir, family.getName())
				familyDir.mkdir()
				def familyIndexFile = new File(familyDir, "deviceIndex.txt")

				familyIndexFile.withPrintWriter { deviceListWriter ->

					String pattern = "_db.dat";
					family.list().each { part ->
						if(part.endsWith(pattern)){
							deviceListWriter.println(part.replace(pattern, ""))
						}
					}
				}

			}
		}
	}
}