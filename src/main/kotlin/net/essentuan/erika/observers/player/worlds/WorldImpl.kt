package net.essentuan.erika.observers.player.worlds

import com.busted_moments.buster.api.Profile
import com.busted_moments.buster.api.World
import net.essentuan.erika.db.struct.player.ProfileModel
import net.essentuan.esl.json.Json
import java.util.Date
import java.util.UUID

internal class WorldImpl(
    override val name: String,
    override val firstSeen: Date = Date(),
    internal val players: MutableMap<UUID, ProfileModel> = mutableMapOf()
) : Json.Model, World {
    override val size: Int
        get() = players.size

    override fun isEmpty(): Boolean = players.isEmpty()

    override fun contains(uuid: UUID): Boolean = uuid in players

    override fun containsAll(elements: Collection<Profile>): Boolean {
        for (profile in elements)
            if (profile !in this)
                return false

        return true
    }

    override fun iterator(): Iterator<Profile> = players.values.iterator()
}