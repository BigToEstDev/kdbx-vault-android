package ru.kino.dev.ffi

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import ru.kino.dev.core.CoreException

/**
 * The envelope every native call comes back in.
 *
 * A failing operation is data, not an exception: a wrong password or a name already taken are ordinary
 * outcomes. The bridge only throws when it is itself broken - a panic, or json it could not build - and
 * that arrives as a [RuntimeException] from jni, not through here.
 */
internal object FfiEnvelope {

    /**
     * Lenient on unknown keys on purpose: the core may start returning a field the app does not know yet,
     * and an older app dropping it is better than an older app crashing.
     */
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Reads a successful payload out of the envelope, or throws [CoreException] with the kind and the
     * message the core reported.
     */
    fun <T> unwrap(envelope: String, payload: DeserializationStrategy<T>): T {
        val parsed = json.decodeFromString(Envelope.serializer(), envelope)

        parsed.err?.let { throw CoreException(kind = it.kind, message = it.message) }

        val ok = parsed.ok ?: throw CoreException(
            kind = MALFORMED_ENVELOPE,
            message = "The answer had neither an 'ok' nor an 'err': $envelope",
        )

        return json.decodeFromJsonElement(payload, ok)
    }

    @Serializable
    private data class Envelope(
        val ok: JsonElement? = null,
        val err: Failure? = null,
    )

    @Serializable
    private data class Failure(
        val kind: String,
        val message: String,
    )

    /** Kind reported when the envelope itself does not make sense - a bug in the bridge, not a failure. */
    const val MALFORMED_ENVELOPE: String = "MalformedEnvelope"
}

/** Arguments of `generate_password`, shaped exactly as the core's `PasswordGenerationOptions`. */
@Serializable
internal data class PasswordOptionsDto(
    val length: Int,
    val numbers: Boolean,
    @SerialName("lowercase_letters") val lowercaseLetters: Boolean,
    @SerialName("uppercase_letters") val uppercaseLetters: Boolean,
    val symbols: Boolean,
    val spaces: Boolean,
    @SerialName("exclude_similar_characters") val excludeSimilarCharacters: Boolean,
    val strict: Boolean,
)

/** Result of `generate_password`. */
@Serializable
internal data class GeneratedPasswordDto(val password: String)

/**
 * Arguments of `create_and_write_to_writer`.
 *
 * The only place where the argument shape is the core's own rather than ours: `NewDatabase` keeps its
 * fields private and can only be built through serde, so its json is the contract. Hence
 * `database_file_name` for what everything else calls `db_key`, and the nested kdf object.
 */
@Serializable
internal data class NewDatabaseDto(
    @SerialName("database_file_name") val databaseFileName: String,
    @SerialName("file_name") val fileName: String?,
    @SerialName("database_name") val databaseName: String,
    @SerialName("database_description") val databaseDescription: String?,
    val password: String?,
    @SerialName("key_file_name") val keyFileName: String?,
    val kdf: KdfDto,
    @SerialName("cipher_id") val cipherId: String,
)

/**
 * The key derivation function of a new database.
 *
 * `algorithm` is the tag of the core's enum, the rest are its parameters. An empty salt means "generate
 * one": the core fills it while creating the database.
 */
@Serializable
internal data class KdfDto(
    val algorithm: String,
    val memory: Long,
    val iterations: Long,
    val parallelism: Int,
    val salt: List<Byte> = emptyList(),
)

/** Result of `read_kdbx` and of `create_and_write_to_writer`: an open database. */
@Serializable
internal data class KdbxLoadedDto(
    @SerialName("db_key") val dbKey: String,
    @SerialName("database_name") val databaseName: String,
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("key_file_name") val keyFileName: String? = null,
)

/** Result of `save_kdbx_to_writer`. The bytes of the file travel beside the envelope, not inside it. */
@Serializable
internal data class KdbxSavedDto(
    @SerialName("db_key") val dbKey: String,
    @SerialName("database_name") val databaseName: String,
)

/** Arguments of every command that works on an open database. */
@Serializable
internal data class DbKeyDto(@SerialName("db_key") val dbKey: String)

/** Arguments of `read_kdbx`. The bytes of the file are passed beside them. */
@Serializable
internal data class ReadKdbxDto(
    @SerialName("db_key") val dbKey: String,
    val password: String?,
    @SerialName("key_file_name") val keyFileName: String?,
    @SerialName("file_name") val fileName: String?,
)

/**
 * What a command that changed something answers when it has nothing to report back - closing a
 * database, moving a group, sorting.
 *
 * One shape for all of them rather than a field named after each command: there is nothing to read here
 * beyond "the call went through", and the failures that matter arrive as `err` instead.
 */
@Serializable
internal data class DoneDto(val done: Boolean)

/** Arguments naming one group of an open database. */
@Serializable
internal data class GroupIdDto(
    @SerialName("db_key") val dbKey: String,
    @SerialName("group_uuid") val groupUuid: String,
)

/**
 * A group, exactly as the core has it.
 *
 * The one place where a core type is the model on both sides: `insert_group` and `update_group` take a
 * whole group, and a hand written subset would silently drop what the core reads and writes back - the
 * elements of another program's database it does not know. So the ui asks for a group, changes the
 * fields it shows, and sends this same object back.
 */
@Serializable
internal data class GroupDto(
    val uuid: String,
    @SerialName("parent_group_uuid") val parentGroupUuid: String,
    val name: String,
    @SerialName("icon_id") val iconId: Int,
    val notes: String,
    val tags: String,
    @SerialName("is_expanded") val isExpanded: Boolean,
    val times: TimesDto,
    @SerialName("marked_category") val markedCategory: Boolean,
    @SerialName("default_auto_type_sequence") val defaultAutoTypeSequence: String? = null,
    @SerialName("enable_auto_type") val enableAutoType: Boolean? = null,
    @SerialName("enable_searching") val enableSearching: Boolean? = null,
    @SerialName("custom_icon_uuid") val customIconUuid: String? = null,
    @SerialName("group_uuids") val groupUuids: List<String> = emptyList(),
    @SerialName("entry_uuids") val entryUuids: List<String> = emptyList(),
)

/** The timestamps kdbx keeps for a group or an entry. Carried through untouched. */
@Serializable
internal data class TimesDto(
    @SerialName("last_modification_time") val lastModificationTime: String,
    @SerialName("creation_time") val creationTime: String,
    @SerialName("last_access_time") val lastAccessTime: String,
    val expires: Boolean,
    @SerialName("expiry_time") val expiryTime: String,
    @SerialName("location_changed") val locationChanged: String,
    @SerialName("usage_count") val usageCount: Int,
)

/** Arguments of `insert_group` and `update_group`: the whole group goes back. */
@Serializable
internal data class GroupArgsDto(
    @SerialName("db_key") val dbKey: String,
    val group: GroupDto,
)

/**
 * The whole tree of groups in one answer, keyed by uuid.
 *
 * One call rather than a walk from the root: the ui draws the tree at once, and the core has it in
 * memory anyway.
 */
@Serializable
internal data class GroupTreeDto(
    @SerialName("root_uuid") val rootUuid: String,
    @SerialName("recycle_bin_uuid") val recycleBinUuid: String,
    @SerialName("deleted_group_uuids") val deletedGroupUuids: List<String> = emptyList(),
    val groups: Map<String, GroupSummaryDto> = emptyMap(),
)

/** A group as the tree carries it: enough to draw a row, not enough to edit one. */
@Serializable
internal data class GroupSummaryDto(
    val uuid: String,
    @SerialName("parent_group_uuid") val parentGroupUuid: String,
    val name: String,
    @SerialName("icon_id") val iconId: Int,
    @SerialName("custom_icon_uuid") val customIconUuid: String? = null,
    @SerialName("group_uuids") val groupUuids: List<String> = emptyList(),
    @SerialName("entry_uuids") val entryUuids: List<String> = emptyList(),
)

/** Arguments of `new_blank_group`: a group that belongs to no database yet. */
@Serializable
internal data class NewBlankGroupDto(
    @SerialName("mark_as_category") val markAsCategory: Boolean,
)

/** Arguments of `new_blank_group_with_parent`. */
@Serializable
internal data class NewBlankGroupWithParentDto(
    @SerialName("parent_group_uuid") val parentGroupUuid: String,
    @SerialName("mark_as_category") val markAsCategory: Boolean,
)

/** Arguments of `move_group`. */
@Serializable
internal data class MoveGroupDto(
    @SerialName("db_key") val dbKey: String,
    @SerialName("group_uuid") val groupUuid: String,
    @SerialName("new_parent_uuid") val newParentUuid: String,
)

/** Arguments of `sort_sub_groups`. `criteria` is `a_to_z` or `z_to_a`, as the bridge spells it. */
@Serializable
internal data class SortSubGroupsDto(
    @SerialName("db_key") val dbKey: String,
    @SerialName("group_uuid") val groupUuid: String,
    val criteria: String,
)

/** Arguments of `clone_group`. An absent name keeps the name of the original. */
@Serializable
internal data class CloneGroupDto(
    @SerialName("db_key") val dbKey: String,
    @SerialName("group_uuid") val groupUuid: String,
    @SerialName("new_name") val newName: String? = null,
)

/** Result of `clone_group`: the uuid of the copy. */
@Serializable
internal data class ClonedGroupDto(@SerialName("group_uuid") val groupUuid: String)

/** Arguments naming one entry of an open database. */
@Serializable
internal data class EntryIdDto(
    @SerialName("db_key") val dbKey: String,
    @SerialName("entry_uuid") val entryUuid: String,
)

/**
 * An entry as a form, exactly as the core has it - the same arrangement as [GroupDto] and for the same
 * reason: `insert_entry_from_form_data` and `update_entry_from_form_data` take the whole form back.
 *
 * Two names of the core show through and stop here, in this module: `group_uuid` means the *parent
 * group* (a TODO of the upstream), and `parsed_fields` is answer-only - the core ignores it on the way
 * back, and it is kept so the form can be sent in unchanged.
 */
@Serializable
internal data class EntryFormDataDto(
    val uuid: String,
    @SerialName("group_uuid") val parentGroupUuid: String,
    @SerialName("icon_id") val iconId: Int,
    @SerialName("custom_icon_uuid") val customIconUuid: String? = null,
    @SerialName("last_modification_time") val lastModificationTime: String,
    @SerialName("creation_time") val creationTime: String,
    @SerialName("last_access_time") val lastAccessTime: String,
    val expires: Boolean,
    @SerialName("expiry_time") val expiryTime: String,
    val tags: List<String> = emptyList(),
    @SerialName("binary_key_values") val binaryKeyValues: List<BinaryKeyValueDto> = emptyList(),
    @SerialName("history_count") val historyCount: Int,
    @SerialName("entry_type_name") val entryTypeName: String,
    @SerialName("entry_type_uuid") val entryTypeUuid: String,
    @SerialName("entry_type_icon_name") val entryTypeIconName: String? = null,
    val title: String,
    val notes: String,
    @SerialName("standard_section_names") val standardSectionNames: List<String> = emptyList(),
    @SerialName("section_names") val sectionNames: List<String> = emptyList(),
    @SerialName("section_fields") val sectionFields: Map<String, List<KeyValueDataDto>> = emptyMap(),
    @SerialName("auto_type") val autoType: AutoTypeDto,
    @SerialName("parsed_fields") val parsedFields: Map<String, String> = emptyMap(),
)

/**
 * One field of an entry form.
 *
 * `password_score` and `current_opt_token` stay [JsonElement]: both are tagged enums of the core whose
 * only use is being shown, and modelling them here would mean a second dictionary to keep in step for
 * no gain. They travel through untouched.
 */
@Serializable
internal data class KeyValueDataDto(
    val key: String,
    val value: String? = null,
    val protected: Boolean,
    val required: Boolean,
    @SerialName("helper_text") val helperText: String? = null,
    @SerialName("data_type") val dataType: String,
    @SerialName("standard_field") val standardField: Boolean,
    @SerialName("select_field_options") val selectFieldOptions: List<String>? = null,
    @SerialName("password_score") val passwordScore: JsonElement? = null,
    @SerialName("current_opt_token") val currentOtpToken: JsonElement? = null,
)

/** An attachment of an entry. Attachments are not in v1; the field is carried so a form round trips. */
@Serializable
internal data class BinaryKeyValueDto(
    val key: String,
    val value: String,
    @SerialName("index_ref") val indexRef: Int,
    @SerialName("data_hash") val dataHash: String,
    @SerialName("data_size") val dataSize: Long,
)

/** Auto type settings of an entry. Not edited by this app - kept as read so saving does not lose it. */
@Serializable
internal data class AutoTypeDto(
    val enabled: Boolean,
    @SerialName("default_sequence") val defaultSequence: String? = null,
    val associations: List<AssociationDto> = emptyList(),
    @SerialName("data_transfer_obfuscation") val dataTransferObfuscation: Int = 0,
)

@Serializable
internal data class AssociationDto(
    val window: String,
    @SerialName("key_stroke_sequence") val keyStrokeSequence: String? = null,
)

/** An entry as a list shows it. */
@Serializable
internal data class EntrySummaryDto(
    val uuid: String,
    @SerialName("parent_group_uuid") val parentGroupUuid: String,
    val title: String? = null,
    @SerialName("secondary_title") val secondaryTitle: String? = null,
    @SerialName("entry_type_name") val entryTypeName: String,
    @SerialName("entry_type_uuid") val entryTypeUuid: String,
    @SerialName("icon_id") val iconId: Int,
    @SerialName("custom_icon_uuid") val customIconUuid: String? = null,
    @SerialName("history_index") val historyIndex: Int? = null,
    @SerialName("modified_time") val modifiedTime: Long? = null,
    @SerialName("created_time") val createdTime: Long? = null,
)

/** Arguments of `insert_entry_from_form_data` and `update_entry_from_form_data`. */
@Serializable
internal data class EntryFormArgsDto(
    @SerialName("db_key") val dbKey: String,
    @SerialName("form_data") val formData: EntryFormDataDto,
)

/**
 * Which entries to list.
 *
 * The bridge spells it `{"kind": "all_entries"}` or `{"kind": "group", "value": "…"}`, snake_case and
 * internally tagged - the core's own `camelCase`, externally tagged enum never reaches this side.
 */
@Serializable
internal data class EntryCategoryDto(
    val kind: String,
    val value: String? = null,
)

/** Arguments of `entry_summary_data`. */
@Serializable
internal data class EntrySummaryArgsDto(
    @SerialName("db_key") val dbKey: String,
    val category: EntryCategoryDto,
)

/** Arguments of `new_entry_form_data_by_id`. */
@Serializable
internal data class NewEntryFormDto(
    @SerialName("db_key") val dbKey: String,
    @SerialName("entry_type_uuid") val entryTypeUuid: String,
    @SerialName("parent_group_uuid") val parentGroupUuid: String? = null,
)

/** Arguments of `move_entry`. */
@Serializable
internal data class MoveEntryDto(
    @SerialName("db_key") val dbKey: String,
    @SerialName("entry_uuid") val entryUuid: String,
    @SerialName("new_parent_uuid") val newParentUuid: String,
)

/** Arguments of `clone_entry` - the four answers of the "duplicate" dialog, flat. */
@Serializable
internal data class CloneEntryDto(
    @SerialName("db_key") val dbKey: String,
    @SerialName("entry_uuid") val entryUuid: String,
    @SerialName("new_title") val newTitle: String? = null,
    @SerialName("parent_group_uuid") val parentGroupUuid: String,
    @SerialName("keep_histories") val keepHistories: Boolean,
    @SerialName("link_by_reference") val linkByReference: Boolean,
)

/** Result of `clone_entry`: the uuid of the copy. */
@Serializable
internal data class ClonedEntryDto(@SerialName("entry_uuid") val entryUuid: String)

/**
 * Arguments naming one version in the history of an entry.
 *
 * The index is a position in the list `history_entries_summary` answered, not an identity: deleting a
 * version renumbers the rest, so the ui reloads the summary after every delete.
 */
@Serializable
internal data class HistoryIndexDto(
    @SerialName("db_key") val dbKey: String,
    @SerialName("entry_uuid") val entryUuid: String,
    val index: Int,
)

/**
 * The 2fa settings of an entry, as the bridge spells them.
 *
 * `secret_or_url` is either the shared secret or a whole `otpauth://` url - the core tells them apart.
 * `hash_algorithm` is `sha1`, `sha256` or `sha512`: the core's own `SHA1` / `SHA256` / `SHA512` stays
 * behind the boundary.
 */
@Serializable
internal data class OtpSettingsDto(
    @SerialName("secret_or_url") val secretOrUrl: String,
    val period: Long? = null,
    val digits: Int? = null,
    @SerialName("hash_algorithm") val hashAlgorithm: String? = null,
)

/** Arguments of `set_entry_otp`: the settings, flattened next to the entry they belong to. */
@Serializable
internal data class SetEntryOtpDto(
    @SerialName("db_key") val dbKey: String,
    @SerialName("entry_uuid") val entryUuid: String,
    @SerialName("secret_or_url") val secretOrUrl: String,
    val period: Long? = null,
    val digits: Int? = null,
    @SerialName("hash_algorithm") val hashAlgorithm: String? = null,
)

/** Arguments of `entry_list_current_otps`: the rows on screen, not the whole database. */
@Serializable
internal data class CurrentOtpsDto(
    @SerialName("db_key") val dbKey: String,
    @SerialName("entry_uuids") val entryUuids: List<String>,
)

/**
 * The current code of one entry.
 *
 * `ttl` is how long *this* code is still good for, in seconds - the ticking coroutine that asks for
 * the codes uses it to decide when to ask again.
 */
@Serializable
internal data class EntryOtpTokenDto(
    @SerialName("entry_uuid") val entryUuid: String,
    @SerialName("otp_field_name") val otpFieldName: String,
    val token: String,
    val ttl: Long,
    val period: Long,
)

/** Arguments of `is_valid_otp_url`. */
@Serializable
internal data class OtpUrlDto(@SerialName("otp_url") val otpUrl: String)

/** Result of `form_otp_url` - the `otpauth://` url behind a qr code. */
@Serializable
internal data class FormedOtpUrlDto(@SerialName("otp_url") val otpUrl: String)

/** Result of `is_valid_otp_url`. Not a failure: the ui asks while the user is still typing. */
@Serializable
internal data class OtpUrlValidityDto(val valid: Boolean)

/** Arguments of `search_term`. The core matches it against every field, protected ones included. */
@Serializable
internal data class SearchTermDto(
    @SerialName("db_key") val dbKey: String,
    val term: String,
)

/**
 * The entries matching a term, with the term beside them.
 *
 * The term comes back so a screen can drop the answer to a search the user has already typed past -
 * the calls are asynchronous and may finish out of order.
 */
@Serializable
internal data class EntrySearchResultDto(
    val term: String,
    @SerialName("entry_items") val entryItems: List<EntrySummaryDto> = emptyList(),
)

/** Every tag in the database - of entries and of groups, kept apart. */
@Serializable
internal data class AllTagsDto(
    @SerialName("entry_tags") val entryTags: List<String> = emptyList(),
    @SerialName("group_tags") val groupTags: List<String> = emptyList(),
)

/** Arguments of `unlock_kdbx` - the same credentials as opening, checked against the stored key. */
@Serializable
internal data class UnlockDto(
    @SerialName("db_key") val dbKey: String,
    val password: String?,
    @SerialName("key_file_name") val keyFileName: String?,
)

/** Arguments of `rename_db_key`: the file moved, or "save as" wrote it somewhere else. */
@Serializable
internal data class RenameDbKeyDto(
    @SerialName("old_db_key") val oldDbKey: String,
    @SerialName("new_db_key") val newDbKey: String,
)

/** Result of `is_db_locked`. */
@Serializable
internal data class LockedDto(val locked: Boolean)

/**
 * Result of `is_db_opened`.
 *
 * The one question about a database that cannot fail: one that is not open answers `false` instead of
 * `DbKeyNotFound`, which is the point - it is asked about a uri from the recent list after the process
 * was killed, when the core may hold nothing at all.
 */
@Serializable
internal data class OpenedDto(val opened: Boolean)

/**
 * When the database was last read and written, and whether it holds edits the file does not.
 *
 * `save_pending` is what an "unsaved changes" prompt and the save on going to the background read.
 */
@Serializable
internal data class ContextStatusesDto(
    @SerialName("last_read_time") val lastReadTime: String? = null,
    @SerialName("last_write_time") val lastWriteTime: String? = null,
    @SerialName("save_pending") val savePending: Boolean,
)

/**
 * The settings of a database, exactly as the core has them.
 *
 * The fourth and last core type that travels both ways: `set_db_settings` takes the whole thing back.
 * It is also how the credentials are changed - `password_changed` / `key_file_changed` beside the new
 * values - so the screen reads the settings, edits them and sends them in. Editing them changes the
 * database in memory; the file gets it on the next save.
 */
@Serializable
internal data class DbSettingsDto(
    val kdf: KdfSettingsDto,
    @SerialName("cipher_id") val cipherId: String,
    val password: String? = null,
    @SerialName("key_file_name") val keyFileName: String? = null,
    @SerialName("password_used") val passwordUsed: Boolean,
    @SerialName("key_file_used") val keyFileUsed: Boolean,
    @SerialName("password_changed") val passwordChanged: Boolean,
    @SerialName("key_file_changed") val keyFileChanged: Boolean,
    @SerialName("key_file_name_part") val keyFileNamePart: String? = null,
    @SerialName("database_file_name") val databaseFileName: String,
    val meta: DbMetaDto,
)

/**
 * The key derivation of an existing database.
 *
 * Not [KdfDto], which is what *creating* one takes: there the salt is passed and generated, here the
 * core answers with the version of the algorithm instead.
 */
@Serializable
internal data class KdfSettingsDto(
    val algorithm: String,
    val memory: Long,
    val iterations: Long,
    val parallelism: Int,
    val version: Int,
)

/** The metadata of a database - what a settings screen shows and edits. */
@Serializable
internal data class DbMetaDto(
    @SerialName("database_name") val databaseName: String,
    @SerialName("database_description") val databaseDescription: String,
    @SerialName("history_max_items") val historyMaxItems: Int,
    @SerialName("history_max_size") val historyMaxSize: Long,
)

/** Arguments of `set_db_settings`. */
@Serializable
internal data class SetDbSettingsDto(
    @SerialName("db_key") val dbKey: String,
    val settings: DbSettingsDto,
)

/**
 * Arguments of `generate_key_file`.
 *
 * A path inside the app's own storage, never a SAF uri: the core reads and writes key files itself.
 * An existing file is not replaced - the answer is the kind `AlreadyExists`, and asking "replace it?"
 * is this side's job.
 */
@Serializable
internal data class GenerateKeyFileDto(
    @SerialName("key_file_name") val keyFileName: String,
)

/**
 * The checksum the core holds for a database file, as the bytes it is.
 *
 * Not hex: the app only hands it back or compares it, and an encoding would be one more thing for
 * both sides to agree on.
 */
@Serializable
internal data class ChecksumDto(val checksum: List<Int> = emptyList())

/**
 * What a merge changed.
 *
 * There is no undo: the merge changes the database in memory at once, and [differentDatabases] is
 * only known afterwards - so a warning about merging two unrelated databases is shown as a fact, and
 * "cancel" there means closing without saving. The file itself is untouched, because this side is
 * what writes it.
 */
@Serializable
internal data class MergeResultDto(
    @SerialName("added_groups") val addedGroups: List<MergedGroupDto> = emptyList(),
    @SerialName("updated_groups") val updatedGroups: List<MergedGroupDto> = emptyList(),
    @SerialName("parent_changed_groups") val parentChangedGroups: List<MergedGroupDto> = emptyList(),
    @SerialName("added_entries") val addedEntries: List<MergedEntryDto> = emptyList(),
    @SerialName("updated_entries") val updatedEntries: List<MergedEntryDto> = emptyList(),
    @SerialName("parent_changed_entries") val parentChangedEntries: List<MergedEntryDto> = emptyList(),
    @SerialName("permanently_deleted_entries")
    val permanentlyDeletedEntries: List<MergedEntryDto> = emptyList(),
    @SerialName("permanently_deleted_groups")
    val permanentlyDeletedGroups: List<MergedGroupDto> = emptyList(),
    @SerialName("meta_data_changed") val metaDataChanged: Boolean,
    @SerialName("merge_done") val mergeDone: Boolean,
    @SerialName("different_databases") val differentDatabases: Boolean,
)

/** A group a merge touched, named well enough to list it. */
@Serializable
internal data class MergedGroupDto(
    val uuid: String,
    val name: String,
    @SerialName("parent_group_uuid") val parentGroupUuid: String? = null,
    @SerialName("previous_parent_group_uuid") val previousParentGroupUuid: String? = null,
)

/** An entry a merge touched. */
@Serializable
internal data class MergedEntryDto(
    val uuid: String,
    val name: String,
    @SerialName("parent_group_uuid") val parentGroupUuid: String? = null,
    @SerialName("previous_parent_group_uuid") val previousParentGroupUuid: String? = null,
)

/**
 * Arguments of `combined_category_details`.
 *
 * `grouping` is `as_group_categories`, `as_types` or `as_tags` - what the tiles below the standard
 * ones are grouped by.
 */
@Serializable
internal data class CategoriesArgsDto(
    @SerialName("db_key") val dbKey: String,
    val grouping: String,
)

/** The tiles of the home screen: the standard ones, and those of the chosen grouping. */
@Serializable
internal data class EntryCategoriesDto(
    @SerialName("general_categories") val generalCategories: List<CategoryDetailDto> = emptyList(),
    @SerialName("grouping_kind") val groupingKind: String,
    @SerialName("grouped_categories") val groupedCategories: List<CategoryDetailDto> = emptyList(),
)

/** One tile, with the counts it shows. */
@Serializable
internal data class CategoryDetailDto(
    val title: String,
    @SerialName("display_title") val displayTitle: String? = null,
    @SerialName("entries_count") val entriesCount: Int,
    @SerialName("groups_count") val groupsCount: Int,
    @SerialName("icon_id") val iconId: Int,
    @SerialName("icon_name") val iconName: String? = null,
    @SerialName("entry_type_uuid") val entryTypeUuid: String? = null,
    @SerialName("group_uuid") val groupUuid: String? = null,
    @SerialName("parent_group_uuid") val parentGroupUuid: String? = null,
    @SerialName("tag_id") val tagId: String? = null,
)

/**
 * The entry types to create an entry from.
 *
 * `custom` is answered and stays empty in v1: making custom types needs an editor screen, which is
 * not in v1, but a database written elsewhere may well carry them.
 */
@Serializable
internal data class EntryTypeHeadersDto(
    val standard: List<EntryTypeHeaderDto> = emptyList(),
    val custom: List<EntryTypeHeaderDto> = emptyList(),
)

@Serializable
internal data class EntryTypeHeaderDto(
    val uuid: String,
    val name: String,
    @SerialName("icon_name") val iconName: String? = null,
)

/**
 * A generated password together with its rating.
 *
 * One call rather than generating and then scoring: the rating belongs to *this* password, and two
 * calls would let a screen show the rating of one the user is no longer looking at.
 */
@Serializable
internal data class AnalyzedPasswordDto(
    val password: String,
    @SerialName("analyzed_password") val analyzedPassword: String,
    val score: PasswordScoreDto,
    val length: Int,
    @SerialName("numbers_count") val numbersCount: Int,
    @SerialName("lowercase_letters_count") val lowercaseLettersCount: Int,
    @SerialName("uppercase_letters_count") val uppercaseLettersCount: Int,
    @SerialName("symbols_count") val symbolsCount: Int,
    @SerialName("spaces_count") val spacesCount: Int,
    @SerialName("other_characters_count") val otherCharactersCount: Int,
    @SerialName("consecutive_count") val consecutiveCount: Int,
    @SerialName("non_consecutive_count") val nonConsecutiveCount: Int,
    @SerialName("progressive_count") val progressiveCount: Int,
    @SerialName("is_common") val isCommon: Boolean,
)

/** How strong the password is: the name of the band, and the text to show. */
@Serializable
internal data class PasswordScoreDto(
    val name: String,
    @SerialName("raw_value") val rawValue: Double,
    @SerialName("score_text") val scoreText: String,
)

/** An empty json object, for commands that take no arguments. */
internal val NO_ARGUMENTS: JsonObject = JsonObject(emptyMap())
