package net.essentuan.erika.features.content

import com.busted_moments.buster.api.Profile
import com.busted_moments.buster.protocol.serverbound.ContentStage
import net.essentuan.erika.db.struct.fuy.BusterAccount
import net.essentuan.erika.db.struct.player.search.invoke
import net.essentuan.erika.features.content.modifiers.ContentModifier
import net.essentuan.erika.features.content.type.ContentType
import net.essentuan.erika.framework.db.api.table.schema.Index
import net.essentuan.erika.framework.db.`object`.types.Struct
import net.essentuan.erika.framework.db.table.StandardTable
import net.essentuan.esl.get
import net.essentuan.esl.rx.map
import net.essentuan.esl.time.duration.Duration
import org.reactivestreams.Publisher
import java.util.Date
import java.util.UUID

data class ContentRecord(
    val type: ContentType,
    val start: Date,
    val end: Date,
    val partyMembers: Set<UUID>,
    private val stages: List<ContentStage>,
    val modifiers: ContentModifier
) : Struct<ContentRecord.Table>(), List<ContentStage> by stages {
    val duration: Duration
        get() = Duration(start, end)

    val party: Publisher<Profile>
        get() = Profile { +partyMembers }
            .map { it.second.get() }

    companion object Table : StandardTable<BusterAccount>() {
        init {
            schema {
                ContentRecord::type {
                    index { +Index.Type.ASCENDING }
                }

                ContentRecord::start {
                    index { +Index.Type.ASCENDING }
                }

                ContentRecord::end {
                    index { +Index.Type.ASCENDING }
                }

                ContentRecord::partyMembers {
                    index { +Index.Type.ASCENDING }
                }

                ContentRecord::modifiers {
                    index { +Index.Type.ASCENDING }
                }
            }
        }
    }
}

val ContentStage.duration: Duration
    get() = Duration(start, end)