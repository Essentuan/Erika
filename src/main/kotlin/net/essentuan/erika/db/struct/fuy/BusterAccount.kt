package net.essentuan.erika.db.struct.fuy

import com.busted_moments.buster.api.Account
import net.essentuan.erika.framework.db.api.table.schema.Attribute
import net.essentuan.erika.framework.db.api.table.schema.Index
import net.essentuan.erika.framework.db.`object`.store
import net.essentuan.erika.framework.db.`object`.types.Struct
import net.essentuan.erika.framework.db.table.StandardTable
import net.essentuan.erika.db.struct.guild.member.MemberModel
import net.essentuan.erika.db.struct.player.ProfileModel
import net.essentuan.erika.db.struct.player.ProfileModel.Table.external
import net.essentuan.erika.fetch.wynncraft.player.playtime
import net.essentuan.esl.fetch.fetch
import net.essentuan.esl.json.Json
import net.essentuan.esl.json.json
import net.essentuan.esl.model.Model.Companion.export
import net.essentuan.esl.time.duration.hours
import net.essentuan.esl.time.duration.weeks
import net.essentuan.esl.time.extensions.timeSince
import java.util.Date

data class BusterAccount(
    override val profile: ProfileModel,
    val member: MemberModel,
    override var preferences: Account.Preferences = Account.Preferences(),
    private var trusted: Date = Date(0),
) : Struct<BusterAccount.Table>(), Account {
    var banned: Boolean = false
        set(value) {
            field = value

            enqueue()
        }

    suspend fun isTrusted(): Boolean {
        when {
            banned -> return false
            trusted.time == -1L -> return true
            trusted.timeSince() < 1.weeks -> return false
        }

        return fetch {
            (playtime(profile.uuid) ?: return@fetch false).playtime > 1000.hours
        }.also {
            trusted = if (it)
                Date(-1)
            else
                Date()

            enqueue()
        }
    }

    fun external(): Json = json {
        "profile" to profile.external()
        "preferences" to preferences.export()
    }

    companion object Table : StandardTable<BusterAccount>() {
        init {
            schema {
                BusterAccount::profile {
                    +Attribute.UNIQUE

                    index {
                        +Index.Type.ASCENDING
                    }
                }
            }

            store {
                +BusterAccount::profile { (it as? ProfileModel)?.id ?: it }
            }
        }
    }
}