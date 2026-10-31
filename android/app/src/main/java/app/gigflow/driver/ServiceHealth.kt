package app.gigflow.driver

import android.content.Context

/**
 * Minimal, local-only accessibility lifecycle diagnostics.
 * Records service connection, most recent Uber/Lyft event and an exception's
 * class name. Never persists screen text, fare amounts or exception messages.
 */
object ServiceHealth {
    private const val PREFS = "moonbeam_service_health"
    private const val EVENT_WRITE_INTERVAL_MS = 15_000L
    private var lastEventWriteAt = 0L

    data class Snapshot(
        val connectedAt: Long,
        val disconnectedAt: Long,
        val lastEventAt: Long,
        val lastEventApp: String,
        val lastErrorAt: Long,
        val lastErrorType: String,
    )

    fun connected(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("connected_at", System.currentTimeMillis())
            .apply()
    }

    fun disconnected(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("disconnected_at", System.currentTimeMillis())
            .apply()
    }

    fun event(ctx: Context, pkg: String) {
        val now = System.currentTimeMillis()
        if (now - lastEventWriteAt < EVENT_WRITE_INTERVAL_MS) return
        lastEventWriteAt = now
        val app = when (pkg) {
            "com.uber.driver" -> "Uber"
            "com.lyft.driver" -> "Lyft"
            else -> return
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("last_event_at", now)
            .putString("last_event_app", app)
            .apply()
    }

    fun error(ctx: Context, error: Exception) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("last_error_at", System.currentTimeMillis())
            .putString("last_error_type", error.javaClass.simpleName.take(70))
            .apply()
    }

    fun snapshot(ctx: Context): Snapshot {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Snapshot(
            connectedAt = p.getLong("connected_at", 0),
            disconnectedAt = p.getLong("disconnected_at", 0),
            lastEventAt = p.getLong("last_event_at", 0),
            lastEventApp = p.getString("last_event_app", "").orEmpty(),
            lastErrorAt = p.getLong("last_error_at", 0),
            lastErrorType = p.getString("last_error_type", "").orEmpty(),
        )
    }
}
