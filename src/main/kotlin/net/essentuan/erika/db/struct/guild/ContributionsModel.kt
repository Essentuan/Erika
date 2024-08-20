package net.essentuan.erika.db.struct.guild

import com.busted_moments.buster.api.Contribution
import com.busted_moments.buster.api.Contributions
import net.essentuan.erika.framework.events.Event
import net.essentuan.erika.db.struct.guild.member.MemberModel
import net.essentuan.erika.observers.guilds.events.GuildEvent
import net.essentuan.esl.json.Json
import net.essentuan.esl.json.json
import java.util.Date

data class ContributionsModel(
    override val entries: MutableList<Contribution> = mutableListOf()
) : Json.Model, Contributions.Helper {
    override var total: Long = 0
        private set

    constructor(vararg contribution: Contribution) : this(
        mutableListOf(*contribution)
    ) {
        total = contribution.sumOf { it.amount }
    }

    @Synchronized
    fun MemberModel.update(contributed: Long, at: Date = Date(),): Event? {
        if (total >= contributed)
            return null

        val contribution = Contribution(at, contributed - total)
        entries.add(contribution)
        total = contributed

        return GuildEvent.Member.Contributed(this, contribution.amount)
    }

    companion object {
        fun Contribution.external() = json {
            "at" to at.time
            "amount" to amount
        }

        fun Contributions.external() = json {
            "entries" to this@external.map { it.external() }
            "total" to total
        }
    }
}