package net.essentuan.erika.ktor.routes

import net.essentuan.erika.buster.releases.repos.Candidate
import net.essentuan.erika.buster.releases.repos.FuyRepo
import net.essentuan.erika.ktor.GET
import net.essentuan.erika.ktor.Route

object BusterRoute : Route.Container() {
    @GET("buster/release")
    private fun release(mc: String): Candidate? =
        FuyRepo[mc]
}