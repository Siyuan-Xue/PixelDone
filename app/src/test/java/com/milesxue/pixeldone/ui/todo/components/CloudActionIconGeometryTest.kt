package com.milesxue.pixeldone.ui.todo.components

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudActionIconGeometryTest {
    @Test
    fun logoutIsTheExactHorizontalMirrorOfLogin() {
        val login = cloudLoginLogoutPolylines(logout = false)
        val logout = cloudLoginLogoutPolylines(logout = true)

        assertEquals(login.size, logout.size)
        login.zip(logout).forEach { (loginLine, logoutLine) ->
            assertEquals(loginLine.points.size, logoutLine.points.size)
            loginLine.points.zip(logoutLine.points).forEach { (loginPoint, logoutPoint) ->
                assertEquals(CloudActionIconViewport, loginPoint.x + logoutPoint.x, 0f)
                assertEquals(loginPoint.y, logoutPoint.y, 0f)
            }
        }
    }
}
