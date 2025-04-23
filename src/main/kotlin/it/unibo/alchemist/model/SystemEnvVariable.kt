package it.unibo.alchemist.model

import it.unibo.alchemist.boundary.variables.AbstractPrintableVariable
import java.io.Serializable
import java.util.stream.Stream

class SystemEnvVariable<V: Serializable>(default: V, val variable: String): AbstractPrintableVariable<V>() {
    override fun getDefault(): V {
        return default
    }

    override fun stream(): Stream<V> {
        val value = System.getenv(variable)
        return when {
            value == null -> Stream.of(default)
            value.toDoubleOrNull() != null -> Stream.of(value.toDouble() as V)
            value.equals("true", ignoreCase = true) || value.equals("false", ignoreCase = true) -> Stream.of(value.toBoolean() as V)
            else -> Stream.of(default)
        }
    }

}