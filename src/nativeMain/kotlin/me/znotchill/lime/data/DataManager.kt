package me.znotchill.lime.data

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlIndentation
import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.TomlOutputConfig
import kotlinx.serialization.modules.EmptySerializersModule
import me.znotchill.lime.exceptions.SecurityException
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

object DataManager {
    val fs = FileSystem.SYSTEM
    val baseDir = ".".toPath().resolve("lime").let {
        if (!fs.exists(it)) fs.createDirectories(it)
        fs.canonicalize(it)
    }

    private val inputConfig = TomlInputConfig(
        ignoreUnknownNames = true,
        allowEmptyValues = true
    )

    private val outputConfig = TomlOutputConfig(
        indentation = TomlIndentation.FOUR_SPACES,
        explicitTables = true
    )

    val toml = Toml(
        inputConfig = inputConfig,
        outputConfig = outputConfig,
        serializersModule = EmptySerializersModule()
    )

    private val holders = mutableListOf<DataHolder>()

    fun register(holder: DataHolder) {
        holders.add(holder)
    }

    fun initialize() {
        holders.forEach { holder ->
            println("Loading module: ${holder.id}")
            try {
                holder.load()
            } catch (e: Exception) {
                println("Failed to load ${holder.id}: ${e.message}")
            }
        }
    }

    fun resolve(segment: String): Path {
        val resolved = baseDir.resolve(segment)
        val canonical = if (fs.exists(resolved)) fs.canonicalize(resolved) else resolved

        if (!canonical.toString().startsWith(baseDir.toString())) {
            throw SecurityException("Escape attempt: $segment")
        }
        return resolved
    }

    fun ensureDir(path: Path) {
        if (!fs.exists(path)) fs.createDirectories(path)
    }
}