package fucknut.report.ingest


import java.lang.System.getProperty
import java.nio.file.*
import java.nio.file.StandardCopyOption
import kotlin.io.path.pathString

public open class MediaConverter {
//"Usage: MediaConverter [ -o <outputspec>] [inputspec...]"
//converts media files to opus/ogg hq 112k vbr anmd duplicates the attachments and metas to the output file

    fun convert(input: Path, output: Path) {
        Files.createDirectories(output)
        when {
            Files.isDirectory(input) -> input.toFile().listFiles()?.forEach { convert(it.toPath(), output) }
            else -> convertFile(input, output.resolve(input.fileName.toString().replaceAfterLast(".", "ogg")))
        }
    }

    fun Path.exists() = Files.exists(this)

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            if (args.isEmpty()) {
                println("Usage: MediaConverter [ -o <outputspec>] [inputspec...]")
                return
            }
            var output = Paths.get(
                getProperty(/*tmpdir in java  sys */ "java.io.tmpdir")
                    ?: throw IllegalStateException("java.io.tmpdir not set")
            )
            val mc = MediaConverter()
            val inputs = mutableListOf<Path>()
            var i = 0
            while (i < args.size) {
                when (args[i]) {
                    "-o" -> output = Paths.get(args[++i])
                    else -> inputs.add(Paths.get(args[i]))
                }
                i++
            }
            inputs.forEach { mc.convert(it, output) }

        }
    }

    fun convertFile(input: Path, outputName: Path) {
        val tempFile = Files.createTempFile("conversion", ".tmp")
        val failFile = outputName.resolveSibling("${outputName.fileName}.fail")
        if (failFile.exists()) {
            println("Skipping failed conversion $input")
            return
        }
        if (outputName.exists()) {
            println("Skipping existing $input")
            return
        }
        println("Converting ${input.fileName} to ${outputName.fileName}")
        val process = ProcessBuilder(
            "ffmpeg", "-i", input.pathString,
            "-vn", "-c", "copy", "-c:a", "libopus", "-b:a", "112k", "-vbr", "on",
            "-map_metadata", "0", "-map_chapters", "0",
            "-map", "0:d?", "-map", "0:s?",
            "-f", "ogg", "-y", tempFile.pathString
        )
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .start()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            println("Conversion failed for $input")
            Files.move(tempFile, failFile, StandardCopyOption.REPLACE_EXISTING)
            return
        }
        Files.move(tempFile, outputName, StandardCopyOption.REPLACE_EXISTING)
    }

}