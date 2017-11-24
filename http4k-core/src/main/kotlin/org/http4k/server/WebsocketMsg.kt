package org.http4k.server

import org.http4k.asString
import org.http4k.core.Body
import org.http4k.lens.Invalid
import org.http4k.lens.LensExtractor
import org.http4k.lens.LensFailure
import org.http4k.lens.LensGet
import org.http4k.lens.LensInjector
import org.http4k.lens.LensSet
import org.http4k.lens.Meta
import org.http4k.lens.Missing
import org.http4k.lens.ParamMeta

internal val meta = Meta(true, "websocket", ParamMeta.ObjectParam, "")

open class WsLensSpec<out OUT>(internal val get: LensGet<WsMessage, OUT>) {
    /**
     * Create a lens for this Spec
     */
    open fun toLens(): WsLens<OUT> {
        return WsLens({ get("")(it).firstOrNull() ?: throw LensFailure(Missing(meta)) })
    }

    /**
     * Create another WebsocketLensSpec which applies the uni-directional transformation to the result. Any resultant Lens can only be used to extract the final type from a WsMessage.
     */
    fun <NEXT> map(nextIn: (OUT) -> NEXT): WsLensSpec<NEXT> = WsLensSpec(get.map(nextIn))
}

/**
 * Represents a bi-directional extraction of an entity from a target Body, or an insertion into a target Body.
 */
open class BiDiWsLensSpec<OUT>(get: LensGet<WsMessage, OUT>,
                               private val set: LensSet<WsMessage, OUT>) : WsLensSpec<OUT>(get) {

    /**
     * Create another BiDiWsLensSpec which applies the bi-directional transformations to the result. Any resultant Lens can be
     * used to extract or insert the final type from/into a WsMessage.
     */
    fun <NEXT> map(nextIn: (OUT) -> NEXT, nextOut: (NEXT) -> OUT) = BiDiWsLensSpec(get.map(nextIn), set.map(nextOut))

    /**
     * Create a lens for this Spec
     */
    override fun toLens(): BiDiWsLens<OUT> {
        val getLens = get("")
        val setLens = set("")
        return BiDiWsLens(
            { getLens(it).let { if (it.isEmpty()) throw LensFailure(Missing(meta)) else it.first() } },
            { out: OUT, target: WsMessage -> setLens(out?.let { listOf(it) } ?: emptyList(), target) }
        )
    }
}

open class WsLens<out FINAL>(private val getLens: (WsMessage) -> FINAL) : LensExtractor<WsMessage, FINAL> {
    override operator fun invoke(target: WsMessage): FINAL = try {
        getLens(target)
    } catch (e: LensFailure) {
        throw e
    } catch (e: Exception) {
        throw LensFailure(Invalid(Meta(true, "websocket", ParamMeta.ObjectParam, "")), cause = e, target = target)
    }
}

/**
 * A BiDiWsLens provides the bi-directional extraction of an entity from a target body, or the insertion of an entity
 * into a target body.
 */
class BiDiWsLens<FINAL>(get: (WsMessage) -> FINAL,
                        private val setLens: (FINAL, WsMessage) -> WsMessage)
    : LensInjector<FINAL, WsMessage>, WsLens<FINAL>(get) {

    @Suppress("UNCHECKED_CAST")
    override operator fun <R : WsMessage> invoke(value: FINAL, target: R): R = setLens(value, target) as R
}

private val root =
    BiDiWsLensSpec<Body>(
        LensGet { _, target -> listOf(target.body) },
        LensSet { _, values, target -> values.fold(target, { m, next -> m.copy(body = next) }) })

fun WsMessage.Companion.binary() = root.map(Body::payload, { Body(it) })
fun WsMessage.Companion.string() = root.map({ it.payload.asString() }, { it: String -> Body(it) })