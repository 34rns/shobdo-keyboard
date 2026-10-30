package app.shobdo.keyboard.ime

import android.content.res.AssetManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class LayoutFormatException(message: String) : Exception(message)

/**
 * Reads a layout page from JSON. The file format is documented in docs/LAYOUT_FORMAT.md and
 * validated by scripts/validate-layouts.py, so this loader can stay small: it maps fields and
 * refuses anything structurally wrong with a message naming the file.
 */
object LayoutLoader {

    const val ASSET_DIR = "layouts"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class LayoutDto(
        val id: String = "",
        val name: String = "",
        val rows: List<List<KeyDto>> = emptyList(),
        val numberRowIndex: Int? = null
    )

    @Serializable
    private data class KeyDto(
        val label: String = "",
        val code: String = "",
        val type: String? = null,
        val widthWeight: Float = 1f,
        @SerialName("long") val longPress: List<String>? = null
    )

    /** Loads `<id>.json` from the `layouts` asset directory. */
    fun load(assets: AssetManager, id: String): KeyboardLayout {
        val source = assets.open("$ASSET_DIR/$id.json").bufferedReader().use { it.readText() }
        return parse(source)
    }

    fun parse(source: String): KeyboardLayout {
        val dto = try {
            json.decodeFromString(LayoutDto.serializer(), source)
        } catch (e: Exception) {
            throw LayoutFormatException("layout JSON is not readable: ${e.message}")
        }
        if (dto.id.isBlank()) throw LayoutFormatException("layout has no 'id'")
        if (dto.rows.isEmpty()) throw LayoutFormatException("layout '${dto.id}' has no 'rows'")

        val rows = dto.rows.mapIndexed { rowIndex, keys ->
            if (keys.isEmpty()) {
                throw LayoutFormatException("layout '${dto.id}' row ${rowIndex + 1} is empty")
            }
            keys.mapIndexed { column, key -> toModel(dto.id, rowIndex, column, key) }
        }
        if (dto.numberRowIndex != null && dto.numberRowIndex !in rows.indices) {
            throw LayoutFormatException("layout '${dto.id}' numberRowIndex ${dto.numberRowIndex} is not a row")
        }
        return KeyboardLayout(
            id = dto.id,
            name = dto.name.ifBlank { dto.id },
            rows = rows,
            numberRowIndex = dto.numberRowIndex
        )
    }

    private fun toModel(layoutId: String, row: Int, column: Int, dto: KeyDto): KeyModel {
        val where = "layout '$layoutId' row ${row + 1} key ${column + 1}"
        // The language key draws its own mark (Latin A, Bangla অ, swap arrow), so it is the one
        // key type allowed to carry no label.
        val drawsItself = dto.type == KeyType.GLOBE.json
        if (dto.label.isEmpty() && !drawsItself) {
            throw LayoutFormatException("$where has no 'label'")
        }
        if (dto.widthWeight <= 0f) throw LayoutFormatException("$where has widthWeight ${dto.widthWeight}; it must be greater than 0")
        val type = try {
            KeyType.fromJson(dto.type)
        } catch (e: IllegalArgumentException) {
            throw LayoutFormatException("$where: ${e.message}")
        }
        val longPress = dto.longPress.orEmpty()
        if (longPress.any { it.isEmpty() }) throw LayoutFormatException("$where has an empty long-press entry")
        return KeyModel(
            label = dto.label,
            code = dto.code,
            type = type,
            widthWeight = dto.widthWeight,
            longPressChars = longPress
        )
    }
}
