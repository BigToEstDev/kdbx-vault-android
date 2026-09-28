package ru.kino.dev.ffi

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
    fun `an unlocked database is read as an opened one`() {
        assertKeysMatch(FfiCommands.UNLOCK_DATABASE, KdbxLoadedDto.serializer())
        assertKeysMatch(FfiCommands.RENAME_DB_KEY, KdbxLoadedDto.serializer())
    }

    @Test
    fun `the state of a database is read exactly as the bridge describes it`() {
        assertKeysMatch(FfiCommands.IS_DATABASE_LOCKED, LockedDto.serializer())
        assertKeysMatch(FfiCommands.IS_DATABASE_OPENED, OpenedDto.serializer())
        assertKeysMatch(FfiCommands.CONTEXT_STATUSES, ContextStatusesDto.serializer())
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

    @Test
    fun `an entry form is read exactly as the bridge describes it`() {
        assertKeysMatch(FfiCommands.GET_ENTRY_FORM_DATA_BY_ID, EntryFormDataDto.serializer())
        assertKeysMatch(FfiCommands.NEW_ENTRY_FORM_DATA_BY_ID, EntryFormDataDto.serializer())
    }

    @Test
    fun `the entries of a category are read exactly as the bridge describes them`() {
        assertKeysMatchList(FfiCommands.ENTRY_SUMMARY_DATA, EntrySummaryDto.serializer())
    }

    @Test
    fun `a cloned entry answers with the uuid of the copy`() {
        assertKeysMatch(FfiCommands.CLONE_ENTRY, ClonedEntryDto.serializer())
    }

    /**
     * The fields of an entry are a map whose keys are *data*: the entry type names them, and the user
     * adds his own. There is no model to compare against, so what is pinned is the only promise there
     * is - an object of strings, which is what `Map<String, String>` decodes.
     */
    @Test
    fun `the fields of an entry stay a map of strings`() {
        val shape = Json
            .parseToJsonElement(contractFile(FfiCommands.ENTRY_KEY_VALUE_FIELDS).readText())
            .let { it as JsonObject }

        assertEquals(setOf("<key>"), shape.keys)
        assertEquals("\"string\"", shape.getValue("<key>").toString())
    }

    /**
     * An old version is the same form as the entry it belongs to - the core builds both the same way -
     * so the model is shared, and this test is what keeps that true.
     */
    @Test
    fun `the history of an entry is read exactly as the bridge describes it`() {
        assertKeysMatchList(FfiCommands.HISTORY_ENTRIES_SUMMARY, EntrySummaryDto.serializer())
        assertKeysMatch(FfiCommands.HISTORY_ENTRY_BY_INDEX, EntryFormDataDto.serializer())
    }

    @Test
    fun `the current codes are read exactly as the bridge describes them`() {
        assertKeysMatchList(FfiCommands.ENTRY_LIST_CURRENT_OTPS, EntryOtpTokenDto.serializer())
    }

    @Test
    fun `the url of a code and its check are read exactly as the bridge describes them`() {
        assertKeysMatch(FfiCommands.FORM_OTP_URL, FormedOtpUrlDto.serializer())
        assertKeysMatch(FfiCommands.IS_VALID_OTP_URL, OtpUrlValidityDto.serializer())
    }

    @Test
    fun `a search result and the tags are read exactly as the bridge describes them`() {
        assertKeysMatch(FfiCommands.SEARCH_TERM, EntrySearchResultDto.serializer())
        assertKeysMatch(FfiCommands.COLLECT_ENTRY_GROUP_TAGS, AllTagsDto.serializer())
    }

    @Test
    fun `the settings of a database are read exactly as the bridge describes them`() {
        assertKeysMatch(FfiCommands.GET_DB_SETTINGS, DbSettingsDto.serializer())
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
            FfiCommands.LOCK_DATABASE,
            FfiCommands.INSERT_GROUP,
            FfiCommands.UPDATE_GROUP,
            FfiCommands.MOVE_GROUP,
            FfiCommands.SORT_SUB_GROUPS,
            FfiCommands.MOVE_GROUP_TO_RECYCLE_BIN,
            FfiCommands.REMOVE_GROUP_PERMANENTLY,
            FfiCommands.INSERT_ENTRY_FROM_FORM_DATA,
            FfiCommands.UPDATE_ENTRY_FROM_FORM_DATA,
            FfiCommands.MOVE_ENTRY,
            FfiCommands.MOVE_ENTRY_TO_RECYCLE_BIN,
            FfiCommands.REMOVE_ENTRY_PERMANENTLY,
            FfiCommands.DELETE_HISTORY_ENTRY_BY_INDEX,
            FfiCommands.DELETE_HISTORY_ENTRIES,
            FfiCommands.SET_DB_SETTINGS,
            FfiCommands.GENERATE_KEY_FILE,
            FfiCommands.SET_ENTRY_OTP,
            FfiCommands.DELETE_ENTRY_OTP,
        ).forEach { command ->
            assertKeysMatch(command, DoneDto.serializer())
        }
    }

    /** The same as [assertKeysMatch], for an answer that is a list - its first element is the shape. */
    private fun assertKeysMatchList(command: String, serializer: KSerializer<*>) {
        val fromContract = Json
            .parseToJsonElement(contractFile(command).readText())
            .let { it as JsonArray }
            .first()
            .let { it as JsonObject }
            .keys

        val fromModel = with(serializer.descriptor) {
            (0 until elementsCount).map { getElementName(it) }.toSet()
        }

        assertEquals("the model of '$command' does not match the contract", fromContract, fromModel)
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
