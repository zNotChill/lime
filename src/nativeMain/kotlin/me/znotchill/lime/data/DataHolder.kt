package me.znotchill.lime.data

import me.znotchill.lime.exceptions.SecurityException
import me.znotchill.lime.log.Loggable
import okio.Path

interface DataHolder : Loggable {
    /**
     * The name of the folder that this [DataHolder] owns
     * (e.g. "config", "stats")
     */
    val id: String

    /**
     * The root directory for this [DataHolder]: /lime/{id}/
     */
    fun getFolder(): Path = DataManager.resolve(id).also {
        DataManager.ensureDir(it)
    }

    fun getFile(fileName: String): Path {
        val folder = getFolder()
        val file = folder.resolve(fileName).normalized()

        val canonicalFolder = DataManager.fs.canonicalize(folder).toString()

        if (!file.toString().startsWith(canonicalFolder)) {
            throw SecurityException("Path traversal attempt within DataHolder: $fileName")
        }

        return file
    }

    fun save()
    fun load()
}