package app.phtn.pulse.util

import dev.emortal.rayfast.area.area3d.Area3d
import dev.emortal.rayfast.area.area3d.Area3dRectangularPrism
import dev.emortal.rayfast.casting.grid.GridCast
import dev.emortal.rayfast.vector.Vector3d
import net.minestom.server.collision.BoundingBox
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.instance.Instance

object Raycast {
    val boundingBoxToArea3dMap: MutableMap<BoundingBox, Area3d> = HashMap()
    private const val tolerance: Double = 0.35

    init {
        Area3d.CONVERTER.register(BoundingBox::class.java) { box ->
            boundingBoxToArea3dMap.computeIfAbsent(box) { it ->
                return@computeIfAbsent Area3dRectangularPrism.of(
                    it.minX() - tolerance, it.minY() - tolerance, it.minZ() - tolerance,
                    it.maxX() + tolerance, it.maxY() + tolerance, it.maxZ() + tolerance
                )
            }

            boundingBoxToArea3dMap[box]
        }
    }

    val Entity.area3d: Area3d
        get() = Area3d.CONVERTER.from(boundingBox)

    @Suppress("INACCESSIBLE_TYPE")
    fun raycastBlock(instance: Instance, startPoint: Point, direction: Vec, maxDistance: Double): Pos? {
        val gridIterator: Iterator<Vector3d> = GridCast.createExactGridIterator(
            startPoint.x(), startPoint.y(), startPoint.z(),
            direction.x(), direction.y(), direction.z(),
            1.0, maxDistance
        )

        while (gridIterator.hasNext()) {
            val gridUnit = gridIterator.next()
            val pos = Pos(gridUnit.x(), gridUnit.y(), gridUnit.z())

            try {
                val hitBlock = instance.getBlock(pos)

                if (!(hitBlock.isAir || hitBlock.isLiquid)) return pos
            } catch (e: NullPointerException) {
                // catch if chunk is not loaded
                break
            }
        }

        return null
    }

    fun raycastEntity(
        instance: Instance,
        startPoint: Point,
        direction: Vec,
        maxDistance: Double,
        hitFilter: (Entity) -> Boolean = { true }
    ): Pair<Entity, Pos>? {

        instance.entities
            .filter { hitFilter.invoke(it) }
            .filter { it.position.distanceSquared(startPoint) <= maxDistance * maxDistance }
            .forEach {
                val area = it.area3d
                val pos = it.position

                //val intersection = it.boundingBox.boundingBoxRayIntersectionCheck(startPoint.asVec(), direction, it.position)

                val intersection = area.lineIntersection(
                    Vector3d.of(startPoint.x() - pos.x, startPoint.y() - pos.y, startPoint.z() - pos.z),
                    Vector3d.of(direction.x(), direction.y(), direction.z())
                )
                if (intersection != null) {
                    return Pair(it, Pos(intersection.x() + pos.x, intersection.y() + pos.y, intersection.z() + pos.z))
                }
            }

        return null
    }

    fun raycast(
        instance: Instance,
        startPoint: Point,
        direction: Vec,
        maxDistance: Double,
        hitFilter: (Entity) -> Boolean = { true }
    ): RaycastResult {
        val blockRaycast = raycastBlock(instance, startPoint, direction, maxDistance)
        val entityRaycast = raycastEntity(instance, startPoint, direction, maxDistance, hitFilter)



        if (entityRaycast == null && blockRaycast == null) {
            return RaycastResult(RaycastResultType.HIT_NOTHING, null, null)
        }

        // block raycast is always true when reached
        if (entityRaycast == null) {
            return RaycastResult(RaycastResultType.HIT_BLOCK, null, blockRaycast)
        }

        // entity raycast is always true when reached
        if (blockRaycast == null) {
            return RaycastResult(RaycastResultType.HIT_ENTITY, entityRaycast.first, entityRaycast.second)
        }

        // Both entity and block check have collided, time to see which is closer!

        val distanceFromEntity = startPoint.distanceSquared(entityRaycast.second)
        val distanceFromBlock = startPoint.distanceSquared(blockRaycast)

        return if (distanceFromBlock > distanceFromEntity) {
            RaycastResult(RaycastResultType.HIT_ENTITY, entityRaycast.first, entityRaycast.second)
        } else {
            RaycastResult(RaycastResultType.HIT_BLOCK, null, blockRaycast)
        }

    }

}

enum class RaycastResultType {
    HIT_ENTITY,
    HIT_BLOCK,
    HIT_NOTHING
}

data class RaycastResult(
    val resultType: RaycastResultType,
    val hitEntity: Entity?,
    val hitPosition: Point?
)