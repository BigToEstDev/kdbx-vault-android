package ru.kino.dev.ffi

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The models here against the shapes Rust pins in `rust/pass-ffi/contract/`.
 *
 * A renamed field in the core changes the json and nothing else: Kotlin would keep compiling and fail on
 * a device, on the one screen that reads that field. The Rust test notices that the shape changed; this
 * one notices that the model no longer matches it.
 *
 * Only the keys are compared, not the types: a type that moves from string to number is a change the Rust
 * side already refuses to let through silently, and duplicating its type names here would add a second
 * dictionary to keep in sync.
 */
@OptIn(ExperimentalSerializationApi::class)
class FfiContractTest {

    @Test
    fun `an opened database is read exactly as the bridge describes it`() {
        assertKeysMatch(FfiCommands.READ_DATABASE, KdbxLoadedDto.serializer())
    }

    @Test
    fun `a created database is read exactly as the bridge describes it`() {
        assertKeysMatch(FfiCommands.CREATE_DATABASE, KdbxLoadedDto.serializer())
    }

    @Test
    fun `a saved database is read exactly as the bridge describes it`() {
        assertKeysMatch(FfiCommands.SAVE_DATABASE, KdbxSavedDto.serializer())
    }

    @Test
    fun `closing answers exactly as the bridge describes it`() {
        assertKeysMatch(FfiCommands.CLOSE_DATABASE, ClosedDto.serializer())
    }

    @Test
    fun `a generated password is read exactly as the bridge describes it`() {
        assertKeysMatch(FfiCommands.GENERATE_PASSWORD, GeneratedPasswordDto.serializer())
    }

    private fun assertKeysMatch(command: String, serializer: KSerializer<*>) {
        val fromContract = Json
            .parseToJsonElement(contractFile(command).readText())
            .let { it as JsonObject }
            .keys

        val fromModel = with(serializer.descriptor) {
            (0 until elementsCount).map { getElementName(it) }.toSet()
        }

        assertEquals("the model of '$command' does not match the contract", fromContract, fromModel)
    }

    // The crate is found by walking up, so the test does not depend on which directory Gradle chose
    private fun contractFile(command: String): File {
        var directory: File? = File(".").absoluteFile
        while (directory != null) {
            val contract = File(directory, "rust/pass-ffi/contract/$command.json")
            if (contract.isFile) return contract
            directory = directory.parentFile
        }
        error("no contract file for '$command' above ${File(".").absolutePath}")
    }
}
