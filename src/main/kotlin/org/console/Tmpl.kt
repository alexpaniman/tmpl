package org.console

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoRunCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.output.TermUi
import com.github.ajalt.clikt.output.TermUi.echo
import com.github.ajalt.clikt.parameters.arguments.*
import com.github.ajalt.clikt.parameters.options.*
import org.jetbrains.kotlin.script.jsr223.KotlinJsr223JvmLocalScriptEngineFactory
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.lang.StringBuilder
import java.nio.file.*

const val yellow = "\u001B[33m"
const val green = "\u001b[32m"
const val blue = "\u001b[34m"
const val red = "\u001b[31m"

const val reset = "\u001B[0m"
fun String.log(color: String, symbol: Char) = "$color[$symbol]$reset $this"
fun String.or() = log(green, '…')
fun String.warn() = log(yellow, '!')
fun String.error() = log(red, '✘')
fun String.success() = log(green, '✔')
fun String.question() = log(blue, '?')

val default = System.getenv("HOME") + "/.tmpl/"
fun String.slash() = if (this.endsWith("/")) this else "$this/"

class Tmpl: NoRunCliktCommand()

private fun File.removeFile(): Boolean {
    var success = true
    if (isDirectory)
        files()?.forEach {
            if (!it.removeFile())
                success = false
        } ?: return false
    val identifier = "'$name' ${if (isDirectory) "directory" else "file"}"
    try {
        Files.delete(toPath())
        if (success)
            echo("$identifier successfully deleted!".success())
        else
            echo("Something went wrong while deleting $identifier".error())
        return true
    } catch (exc: SecurityException) {
        echo("Failed to delete $identifier: access denied".error())
    } catch (exc: NoSuchFileException) {
        echo("Failed to delete $identifier: no such file".error())
    } catch (exc: IOException) {
        echo("Failed to delete $identifier: I/O exception".error())
    } catch (exc: DirectoryNotEmptyException) {
        echo("Failed to delete $identifier: directory is not empty".error())
    }
    return false
}
private fun File.pathTo() = File(absolutePath.removeSuffix("/").substringBeforeLast('/'))
private fun File.appendChild(name: String) = File(absolutePath.slash() + name)
private fun File.createMissingDirs(path: Path = pathTo().toPath()): Boolean {
    val identifier = "'$name' file"
    try {
        Files.createDirectories(path)
        return true
    } catch (exc: SecurityException) {
        echo("Failed to create missing directories for $identifier: access denied".error())
    } catch (exc: FileAlreadyExistsException) {
        echo("Failed to create missing directories for $identifier: file already exists but isn't a directory".error())
    } catch (exc: IOException) {
        echo("Failed to create missing directories for $identifier: I/O exception".error())
    }
    return false
}
private fun File.copyFileTo(destination: File): Boolean {
    if (!destination.createMissingDirs())
        return false
    var success = true
    if (isDirectory)
        files()?.forEach {
            if (!it.copyFileTo(appendChild(it.name)))
                success = false
        } ?: return false
    else {
        val identifier = "'$name' file"
        try {
            Files.copy(toPath(), destination.toPath())
            if (success)
                echo("File '$name' successfully copied!".success())
            else
                echo("Something went wrong while coping $identifier".error())
            return true
        } catch (exc: SecurityException) {
            echo("Failed to copy $identifier: access denied".error())
        } catch (exc: FileAlreadyExistsException) {
            echo("Failed to copy $identifier: file already exists".error())
        } catch (exc: IOException) {
            echo("Failed to copy $identifier: I/O exception".error())
        }
        return false
    }
    return success
}
private fun File.create(): Boolean {
    val identifier = "'$name' file"
    try {
        Files.createFile(toPath())
        return true
    } catch (exc: SecurityException) {
        echo("Failed to create $identifier: access denied".error())
    } catch (exc: FileAlreadyExistsException) {
        echo("Failed to create $identifier: file already".error())
    } catch (exc: IOException) {
        echo("Failed to create $identifier: parent directory does not exist".error())
    }
    return false
}
private fun File.write(text: String): Boolean {
    val identifier = "'$name' file"
    try {
        writeText(text)
        return true
    } catch (exc: FileNotFoundException) {
        echo("Failed to write text in $identifier: file not found".error())
    } catch (exc: SecurityException) {
        echo("Failed to write text in $identifier: access denied".error())
    } catch (exc: IOException) {
        echo("Failed to write text in $identifier: I/O exception".error())
    }
    return false
}
private fun File.createAndWrite(text: String): Boolean {
    if(!createMissingDirs())
        return false
    if(!create())
        return false
    if (!write(text))
        return false
    return true
}
private fun File.files(): Iterable<File>? {
    val identifier = "'$name' directory"
    try {
        return Files.newDirectoryStream(toPath()).asSequence().map { it.toFile() }.asIterable()
    } catch (exc: SecurityException) {
        echo("Failed to open $identifier: access denied")
    } catch (exc: NotDirectoryException) {
        echo("Failed to open $identifier: file isn't directory")
    } catch (exc: IOException) {
        echo("Failed to open $identifier: I/O exception")
    }
    return null
}
private fun File.read(): String? {
    val identifier = "'$name' file"
    try {
        return readText()
    } catch (exc: FileNotFoundException) {
        echo("Failed to read text from $identifier: file not found".error())
    } catch (exc: SecurityException) {
        echo("Failed to read text from $identifier: access denied".error())
    } catch (exc: IOException) {
        echo("Failed to read text from $identifier: I/O exception".error())
    }
    return null
}

class New: CliktCommand(help = "creating new templates from existing sources") {
    private val name by option("-n", "--name", help =
            "the name of the template, " +
            "if not specified, " +
            "the name of the first " +
            "transferred file will be used"
    )
    private val files by argument(help = "" +
            "files or directories " +
            "from which the template " +
            "will be created"
    ).multiple().unique()
    private val directory by option("-d", "--custom-dir", help =
            "directories where " +
            "templates are placed"
    ).default(default)
    private val remove by option("-r", "--remove-source", help =
            "delete source files " +
            "after creating " +
            "the template"
    ).flag()

    override fun run() {
        val content = files.map { File(it) }
        val template: String = if (name == null)
            content.first().name.apply {
                echo("Name is not specified, using $this".warn())
            }
        else name!!
        val target = File(directory.slash() + template)
        var success = true
        content.forEach {
            if (!it.copyFileTo(target.appendChild(it.name)))
                success = false
        }
        if (success)
            echo("Successful creating '$template' template!".success())
        else
            echo("Something went wrong while creating '$template' template!".error())
        success = true
        if (remove) {
            content.forEach {
                if (!it.removeFile())
                    success = false
            }
            if (success)
                echo("Successful removing '$template' template sources!".success())
            else
                echo("Something went wrong while removing '$template' sources!".error())
        }
    }
}
class Use: CliktCommand(help = "creating new projects from templates") {
    private val name by argument(help = "the name of the template")
    private val directory by option("-d", "--custom-dir", help =
    "directories where " +
            "templates are placed"
    ).default(default)

    private val engine = KotlinJsr223JvmLocalScriptEngineFactory().scriptEngine

    private fun processFile(file: File, args: MutableList<String> = ArrayList(), enum: MutableMap<String, String> = HashMap()) {
        if (file.isDirectory)
            file.files()?.sorted()?.forEach { processFile(it, args, enum) } ?: return
        else {
            val text = file.read() ?: return
            val vars = StringBuilder()
            val eval = StringBuilder()

            var readVars = true
            var readEval = true

            val result = StringBuilder()
            val lines = text.lines()
            for ((index, line) in lines.withIndex()) {
                val newLine = index != lines.lastIndex
                if (line.isBlank()) {
                    readEval = false
                    readVars = false
                    result.append(line)
                    if (newLine)
                        result.appendln()
                    continue
                }
                if (line.startsWith("##")) {
                    readVars = false
                    readEval = false
                    result.append(line.removePrefix("#"))
                    if (newLine)
                        result.appendln()
                    continue
                }
                if (line.startsWith("!!")) {
                    readVars = false
                    readEval = false
                    result.append(line.removePrefix("!"))
                    if (newLine)
                        result.appendln()
                    continue
                }
                if (line.startsWith("#") && readVars) {
                    if (vars.isEmpty())
                        vars.append(line.removePrefix("#").trim())
                    else
                        vars.append("|" + line.removePrefix("#").trim())
                    continue
                }
                if (line.startsWith("!") && readEval) {
                    readVars = false
                    eval.appendln(line.removePrefix("!"))
                    continue
                }
                result.append(line)
                if (newLine)
                    result.appendln()
                readEval = false
                readVars = false
            }
            for ((name, type) in vars.split(",").map { it.trim().split(":") }) {
                if (name in args)
                    continue
                if (!("[A-Za-z_]+".toRegex() matches name)) {
                    echo("Illegal variable name '$name'".warn())
                    continue
                }
                val enter = "[A-Za-z][a-z]*".toRegex().findAll(name).map { it.value }.joinToString(" ")
                fun variable(value: Any) {
                    args += name
                    engine.eval("val $name = $value")
                }
                when (type) {
                    "boolean" -> {
                        loop@ while (true) {
                            when (TermUi.prompt("Please choose value for '$enter'[Y/N]".question())?.toUpperCase()) {
                                "Y" -> {
                                    variable(true)
                                    break@loop
                                }
                                "N" -> {
                                    variable(false)
                                    break@loop
                                }
                            }
                            echo("Invalid value! Please try again!".error())
                        }
                    }
                    "string" -> {
                        while (true) {
                            val answer = TermUi.prompt("Please choose value for '$enter' [string]".question())
                            if (answer != null) {
                                variable("\"$answer\"")
                                break
                            }
                            echo("Invalid value! Please try again!".error())
                        }
                    }
                    "float" -> {
                        while (true) {
                            val answer = TermUi.prompt("Please choose value for '$enter' [float]".question())?.toFloatOrNull()
                            if (answer != null) {
                                variable(answer)
                                break
                            }
                            echo("Invalid value! Please try again!".error())
                        }
                    }
                    "int" -> {
                        while (true) {
                            val answer = TermUi.prompt("Please choose value for '$enter' [int]".question())?.toIntOrNull()
                            if (answer != null) {
                                variable(answer)
                                break
                            }
                            echo("Invalid value! Please try again!".error())
                        }
                    }
                    else -> {
                        if ("\\[.+]".toRegex() matches type) {
                            val cases = type.removeSurrounding("[", "]").split("|")
                            while (true) {
                                echo("Please choose an option for '$enter'".question())
                                for ((index, case) in cases.withIndex())
                                    echo("\t$index — ${case.replace("_", "")}".or())
                                val answer = TermUi.prompt("\tSelect from 0..${cases.lastIndex}".question())?.toIntOrNull()
                                if (answer != null) {
                                    val enumName = "${name[0].toUpperCase()}${name.drop(1)}"
                                    engine.eval("""
                                        enum class $enumName {
                                            ${cases.joinToString(", ")}
                                        }
                                        val $name = $enumName.${cases[answer]}
                                    """.trimIndent())
                                    for (case in cases)
                                        enum[case] = "$enumName.$case"
                                    args += name
                                    break
                                }
                                echo("Invalid value! Please try again!".error())
                            }
                        } else echo("Unable to initialize '$name:$type' variable in ${file.name}".warn())
                    }
                }
            }

            fun StringBuilder.completeEnum(): String {
                if (enum.isEmpty())
                    return toString()
                return replace(enum.keys.joinToString("|") { "($it)" }.toRegex()) {
                    enum[it.value]!!
                }
            }

            val processed = StringBuilder()
            val evaluate = StringBuilder()
            var read = false

            var index = 0; while (index < result.length) {
                if (result[index] == '`') {
                    if (index + 1 >= result.length || result[index + 1] != '`') {
                        read = !read
                        if (!read) {
                            processed.append(engine.eval(evaluate.completeEnum()))
                            evaluate.clear()
                        }
                    } else {
                        processed.append("`")
                        index ++
                    }
                } else {
                    if (read) evaluate.append(result[index])
                    else processed.append(result[index])
                }
                index ++
            }
            val path = (engine.eval("""
                | val path: () -> String? = {
                | ${eval.completeEnum()}
                | } 
                | path()
            """.trimMargin()) ?: return) as String
            if (path.endsWith("/")) {
                val it = File(path)
                if (it.createMissingDirs(it.toPath()))
                    echo("Directory '${it.name}' successfully created".success())
                else
                    echo("Something went wrong while creating '${it.name}' directory!".error())
                if (processed.isNotEmpty())
                    echo("File '${it.name}' content don't used".warn())
            } else {
                val it = File(path)
                if (it.createAndWrite(processed.toString()))
                    echo("File '${it.name}' successfully created".success())
                else
                    echo("Something went wrong while creating '${it.name}' file!".error())
            }
        }
    }

    override fun run() {
        val template = File(directory.slash() + name)
        if (!template.exists()) {
            echo("No such '$name' template!".error())
            return
        }
        echo("Creating new project according to '$name' template...".success())
        processFile(template)
        echo("Project successfully created!".success())
    }
}
class Rm: CliktCommand(help = "deleting existing templates") {
    private val name by argument(help = "the name of the template")
    private val directory by option("-d", "--custom-dir", help =
            "directories where " +
            "templates are placed"
    ).default(default)

    override fun run() {
        val template = File(directory.slash() + name)
        if (template.exists()) {
            echo("Deleting '$name' template...".success())
            if (template.removeFile())
                echo("Template '$name' successfully deleted!".success())
            else
                echo("Something went wrong while deleting '$name' template!".error())
        } else {
            echo("No such '$name' template!".error())
        }
    }
}
class Ls: CliktCommand(help = "viewing a list of available templates") {
    private val directory by option("-d", "--custom-dir", help =
            "directories where " +
            "templates are placed"
    ).default(default)
    override fun run() {
        val templates = File(directory).files()?.map { it.name } ?: return
        if (templates.isEmpty())
            echo("No templates available at this time".warn())
        else {
            echo("Available templates:".success())
            for (template in templates)
                echo("\t$template".or())
        }
    }
}

fun main(args: Array<String>) = Tmpl().subcommands(New(), Use(), Rm(), Ls()).main(args)