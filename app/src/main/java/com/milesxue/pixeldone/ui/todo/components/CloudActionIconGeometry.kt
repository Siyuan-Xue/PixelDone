package com.milesxue.pixeldone.ui.todo.components

internal const val CloudActionIconViewport = 22f

internal data class CloudIconPoint(
    val x: Float,
    val y: Float,
)

internal data class CloudIconPolyline(
    val points: List<CloudIconPoint>,
)

/**
 * Pixel-line login geometry based on the approved door-and-arrow reference.
 * Logout is derived by mirroring these exact points instead of being redrawn.
 */
internal fun cloudLoginLogoutPolylines(logout: Boolean): List<CloudIconPolyline> {
    val login = listOf(
        CloudIconPolyline(
            listOf(
                CloudIconPoint(11f, 3f),
                CloudIconPoint(19f, 3f),
                CloudIconPoint(19f, 19f),
                CloudIconPoint(11f, 19f),
            ),
        ),
        CloudIconPolyline(
            listOf(
                CloudIconPoint(3f, 11f),
                CloudIconPoint(16f, 11f),
            ),
        ),
        CloudIconPolyline(
            listOf(
                CloudIconPoint(11f, 6f),
                CloudIconPoint(16f, 11f),
                CloudIconPoint(11f, 16f),
            ),
        ),
    )
    if (!logout) return login
    return login.map { polyline ->
        CloudIconPolyline(
            polyline.points.map { point ->
                point.copy(x = CloudActionIconViewport - point.x)
            },
        )
    }
}
