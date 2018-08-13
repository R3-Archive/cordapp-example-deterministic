package com.example.goody.schemas

import net.corda.core.contracts.MAX_ISSUER_REF_SIZE
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.MAX_HASH_HEX_SIZE
import org.hibernate.annotations.Type
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Index
import javax.persistence.Table

/**
 * An object used to fully qualify the [GoodySchema] family name (i.e. independent of version).
 */
object GoodySchema

/**
 * First version of a goody contract ORM schema that maps all fields of the [org.testing.goody.contracts.Goody]
 * contract state as it stood at the time of writing.
 */
@CordaSerializable
object GoodySchemaV1 : MappedSchema(
    schemaFamily = GoodySchema.javaClass, version = 1, mappedTypes = listOf(PersistentGoodyState::class.java)) {

    @Entity
    @Table(name = "goody_states",
           indexes = [Index(name = "goody_type_idx", columnList = "type")])
    class PersistentGoodyState(
        /** X500Name of owner party **/
        @Column(name = "owner_name", nullable = false, updatable = false)
        val owner: AbstractParty,

        @Column(name = "count", nullable = false, updatable = false)
        val count: Long,

        @Column(name = "type", length = 10, nullable = false, updatable = false)
        val type: String,

        @Column(name = "issuer_key_hash", length = MAX_HASH_HEX_SIZE, nullable = false, updatable = false)
        val issuerPartyHash: String,

        @Column(name = "issuer_ref", length = MAX_ISSUER_REF_SIZE, nullable = false, updatable = false)
        @Type(type = "corda-wrapper-binary")
        val issuerRef: ByteArray
    ) : PersistentState()
}
