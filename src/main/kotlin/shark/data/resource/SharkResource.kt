package shark.data.resource

import lombok.AllArgsConstructor
import org.springframework.core.io.Resource
import shark.data.registry.ResourceLocation

@AllArgsConstructor
class SharkResource (
    val type: ResourceLocation,
    val resource: Resource,
    val baseDirectory: String,
    val path: String,
    val location: ResourceLocation
){
    override fun toString(): String {
        return "SharkResource [$path]"
    }
}
