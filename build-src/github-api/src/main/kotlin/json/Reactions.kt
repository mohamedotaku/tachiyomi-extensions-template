package json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Reactions(
    @SerialName("+1") val plus1: Int,
    @SerialName("-1") val minus1: Int,
    @SerialName("confused") val confused: Int,
    @SerialName("eyes") val eyes: Int,
    @SerialName("heart") val heart: Int,
    @SerialName("hooray") val hooray: Int,
    @SerialName("laugh") val laugh: Int,
    @SerialName("rocket") val rocket: Int,
    @SerialName("total_count") val totalCount: Int,
    @SerialName("url") val url: String
)
