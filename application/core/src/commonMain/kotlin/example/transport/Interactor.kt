package example.transport

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * An interacting entity, which is capable of initiating changes to persistent objects.
 */
@Serializable
open class Interactor(val type: Type, val id: ID, val name: String = "$id") {
    enum class Type {
        UNKNOWN,
        CLIENT,
        BACKEND
    }

    @Serializable
    @JvmInline
    value class ID(val value: String) {
        override fun toString(): String = value
    }

    fun description(): String = when (type) {
        Type.UNKNOWN -> "?"
        Type.CLIENT -> "client '$name'"
        Type.BACKEND -> "backend process '$name'"
    }

    override fun toString(): String = when (type) {
        Type.UNKNOWN -> "?-$id"
        Type.CLIENT -> "C-$id"
        Type.BACKEND -> "B-$id"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Interactor

        if (type != other.type) return false
        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + id.hashCode()
        return result
    }

    companion object {
        val unknown = Interactor(Type.UNKNOWN, ID("?"), "?")
    }
}
