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
    fun `the tree of groups is read exactly as the bridge describes it`() {
        assertKeysMatch(FfiCommands.GROUPS_SUMMARY_DATA, GroupTreeDto.serializer())
    }

    @Test
    fun `a group is read exactly as the bridge describes it`() {
        assertKeysMatch(FfiCommands.GET_GROUP_BY_ID, GroupDto.serializer())
    }

    // A blank group is a group like any other, and the model is shared - so both shapes are checked
    // against it rather than one standing in for the other
    @Test
    fun `a blank group is read exactly as the bridge describes it`() {
        assertKeysMatch(FfiCommands.NEW_BLANK_GROUP, GroupDto.serializer())
        assertKeysMatch(FfiCommands.NEW_BLANK_GROUP_WITH_PARENT, GroupDto.serializer())
    }

    @Test
    fun `a cloned group answers with the uuid of the copy`() {
        assertKeysMatch(FfiCommands.CLONE_GROUP, ClonedGroupDto.serializer())
    }

    /**
     * Everything that only reports that it went through answers the same way, and the point of this
     * test is exactly that: one model on this side, and no command quietly growing a payload the app
     * would never read.
     */
    @Test
    fun `commands that change something and report nothing all answer alike`() {
        listOf(
            FfiCommands.CLOSE_DATABASE,
            FfiCommands.INSERT_GROUP,
            FfiCommands.UPDATE_GROUP,
            FfiCommands.MOVE_GROUP,
            FfiCommands.SORT_SUB_GROUPS,
            FfiCommands.MOVE_GROUP_TO_RECYCLE_BIN,
            FfiCommands.REMOVE_GROUP_PERMANENTLY,
        ).forEach { command ->
            assertKeysMatch(command, DoneDto.serializer())
        }
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
