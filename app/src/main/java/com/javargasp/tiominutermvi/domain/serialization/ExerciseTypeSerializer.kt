package com.javargasp.tiominutermvi.domain.serialization

import com.javargasp.tiominutermvi.domain.model.ExerciseType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Custom serializer for ExerciseType enum.
 * Serializes to/from string values.
 */
object ExerciseTypeSerializer : KSerializer<ExerciseType> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("ExerciseType", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: ExerciseType) {
        encoder.encodeString(value.name)
    }
    
    override fun deserialize(decoder: Decoder): ExerciseType {
        val value = decoder.decodeString()
        return ExerciseType.valueOf(value)
    }
}