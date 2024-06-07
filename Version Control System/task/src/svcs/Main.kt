package svcs

import java.io.File
import java.io.File.separatorChar
import java.io.FileInputStream
import java.security.MessageDigest
import java.time.Instant
import kotlin.io.path.Path
import kotlin.io.path.copyTo

const val VCS = "vcs"
val CONFIG = "$VCS${separatorChar}config.txt"
val INDEX = "$VCS${separatorChar}index.txt"
val LOG = "$VCS${separatorChar}log.txt"
val COMMITS = "$VCS${separatorChar}commits"

fun main(args: Array<String>) {
    makeVcsFolder()

    val command = args.firstOrNull() ?: "--help"
    when (command) {
        "--help" -> help()
        "config" -> config(args.toList())
        "add" -> add(args.toList())
        "log" -> log()
        "commit" -> commit(args.toList())
        "checkout" -> checkout(args.toList())
        else -> printError(command)
    }
}

fun makeVcsFolder() {
    val vcs = File(VCS)
    if (!vcs.exists()) vcs.mkdir()
    val log = File(LOG)
    if (!log.exists()) log.createNewFile()
    val commit = File(COMMITS)
    if (!commit.exists()) commit.mkdir()
}


fun config(commandLine: List<String>) {
    val config = File(CONFIG)
    if (commandLine.size == 1) {
        if (config.exists()) {
            val name = config.readText()
            println("The username is $name.")
        } else {
            println("Please, tell me who you are.")
        }
    } else {
        config.writeText(commandLine[1])
        println("The username is ${commandLine[1]}.")
    }
}

fun add(commandLine: List<String>) {
    val index = File(INDEX)
    if (commandLine.size == 1) {
        if (index.exists()) {
            val trackedFiles = index.readLines()
            println("Tracked files:")
            trackedFiles.forEach { file ->
                println(file)
            }
        } else {
            println("Add a file to the index.")
        }
    } else {
        val newFile = File(commandLine[1])
        if (newFile.exists()) {
            if (index.exists()) {
                index.appendText("\n${commandLine[1]}")
            } else {
                index.writeText(commandLine[1])
            }
            println("The file '${commandLine[1]}' is tracked.")
        } else {
            println("Can't find '${commandLine[1]}'.")
        }
    }
}

fun help() {
    println("These are SVCS commands:")
    println("config     Get and set a username.")
    println("add        Add a file to the index.")
    println("log        Show commit logs.")
    println("commit     Save changes.")
    println("checkout   Restore a file.")
}

fun log() {
    val log = File(LOG)
    val logs = log.readLines()
    if (logs.isNotEmpty()) {
        logs.reversed().forEachIndexed { index, logText ->
            val (hash, author, message) =  logText.split("/")
            if (index != 0) {
                println()
            }
            println("commit $hash")
            println("Author: $author")
            print(message)
        }
    } else {
        println("No commits yet.")
    }
}

fun commit(commandLine: List<String>) {
    val lastCommitFiles = getLastCommitFiles()
    if (commandHasMessage(commandLine)) {
        val message = commandLine[1]
        if (lastCommitFiles.isEmpty()) {
            saveCommit(message)
        } else if (lastCommitFiles.isNotEmpty() && filesHaveBeenChanged()) {
            saveCommit(message)
        } else {
            println("Nothing to commit.")
        }
    }
}

fun commandHasMessage(commandLine: List<String>): Boolean {
    return if (commandLine.size > 1) {
        true
    } else {
        println("Message was not passed.")
        false
    }
}

fun filesHaveBeenChanged(): Boolean {
    val trackedFiles = getTrackedFiles()
    val lastCommitFiles = getLastCommitFiles()
    for (lastCommitFile in lastCommitFiles) {
        for (trackedFile in trackedFiles) {
            if (trackedFile.name == lastCommitFile.name) {
                if (getFileHash(trackedFile, "SHA-256") != getFileHash(lastCommitFile, "SHA-256")) {
                    return true
                }
                break
            }
        }
    }
    return false
}

fun getTrackedFiles(): List<File> {
    val index = File(INDEX)
    return if (index.exists()) {
        index.readLines().map { File(it) }
    } else {
        emptyList()
    }
}

fun getLastCommitFiles(): List<File> {
    val commits = File(COMMITS)

    commits.listFiles()?.let { files ->
        val lastCommit = files.filter { it.isDirectory }.maxByOrNull { it.lastModified() }
        lastCommit?.listFiles()?.let { lastCommitFiles ->
            return lastCommitFiles.toList()
        }
    }

    return emptyList()
}

fun getFileHash(file: File, algorithm: String): String {
    val digest = MessageDigest.getInstance(algorithm)
    FileInputStream(file).use { fis ->
        val byteArray = ByteArray(1024)
        var bytesCount: Int

        while (fis.read(byteArray).also { bytesCount = it } != -1) {
            digest.update(byteArray, 0, bytesCount)
        }
    }
    val bytes = digest.digest()
    val sb = StringBuilder()
    for (byte in bytes) {
        sb.append(String.format("%02x", byte))
    }
    return sb.toString()
}

fun saveCommit(message: String) {
    val uniqueHash = getUniqueCommitHash()
    val newCommitPath = "$COMMITS$separatorChar$uniqueHash"
    val trackedFiles = getTrackedFiles()
    File(newCommitPath).mkdir()
    for (file in trackedFiles) {
        Path(file.name).copyTo(Path("$newCommitPath$separatorChar${file.name}"))
    }
    val config = File(CONFIG).readText()
    val log = File(LOG)
    if (log.readLines().isNotEmpty()) {
        log.appendText("\n$uniqueHash/$config/$message")
    } else {
        log.appendText("$uniqueHash/$config/$message")
    }
    println("Changes are committed.")
}

fun getHash(input: String, algorithm: String): String {
    val digest = MessageDigest.getInstance(algorithm)
    val bytes = digest.digest(input.toByteArray())
    val sb = StringBuilder()
    for (byte in bytes) {
        sb.append(String.format("%02x", byte))
    }
    return sb.toString()
}

fun getUniqueCommitHash(): String {
    val commits = File(COMMITS)
    commits.listFiles()?.let { files ->
        return getHash((files.size + 1).toString(), "SHA-256")
    }
    throw Exception("Don't generate unique commit hash")
}

fun checkout(commandLine: List<String>) {
    if (commandLine.size != 1) {
        val commitHash = commandLine[1]
        val commitDirectory = File(COMMITS)
        val commits = commitDirectory.listFiles()
        if (commits.isNotEmpty()) {
            val commit = commits.firstOrNull { commit -> commit.name == commitHash }
            if (commit != null) {
                commit.listFiles()?.forEach { file ->
                    Path(file.path).copyTo(Path(file.name), overwrite = true)
                }
                println("Switched to commit $commitHash.")
            } else {
                println("Commit does not exist.")
            }
        }
    } else {
        println("Commit id was not passed.")
    }
}

fun printError(command: String) {
    println("'$command' is not a SVCS command.")
}