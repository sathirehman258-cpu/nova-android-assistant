package com.nova.assistant

import com.nova.assistant.domain.tools.ActionRiskManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionRiskManagerTest {

    @Test
    fun `phone call always requires confirmation`() {
        val manager = ActionRiskManager()
        assertTrue(manager.requiresConfirmation("make_phone_call", mapOf("contact" to "Mom")))
    }

    @Test
    fun `low risk action never requires confirmation`() {
        val manager = ActionRiskManager()
        assertFalse(manager.requiresConfirmation("open_app", mapOf("app_name" to "YouTube")))
    }

    @Test
    fun `whatsapp message requires confirmation unless contact trusted`() {
        val manager = ActionRiskManager()
        assertTrue(manager.requiresConfirmation("send_whatsapp_message", mapOf("contact" to "Rahim", "message" to "hi")))
        manager.markContactTrustedForAutoSend("Rahim")
        assertFalse(manager.requiresConfirmation("send_whatsapp_message", mapOf("contact" to "Rahim", "message" to "hi")))
    }
}
