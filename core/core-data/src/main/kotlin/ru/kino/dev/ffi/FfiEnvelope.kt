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

/** An empty json object, for commands that take no arguments. */
internal val NO_ARGUMENTS: JsonObject = JsonObject(emptyMap())
