package com.nova.assistant.domain.tools

import com.nova.assistant.domain.model.RiskLevel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides whether a tool call needs explicit user confirmation before the executor runs it.
 * HIGH risk always requires confirmation. MEDIUM requires it unless the user has explicitly
 * trusted the specific contact/action (see trustedContacts). Never bypasses this for LOW-signal
 * reasons like urgency wording in the transcript — only an explicit prior user setting counts.
 */
@Singleton
class ActionRiskManager @Inject constructor() {

    private val trustedAutoSendContacts = mutableSetOf<String>()

    fun requiresConfirmation(toolName: String, arguments: Map<String, String>): Boolean {
        val def = ToolRegistry.get(toolName) ?: return true
        return when (def.risk) {
            RiskLevel.HIGH -> true
            RiskLevel.MEDIUM -> {
                val contact = arguments["contact"]
                if (toolName == "send_whatsapp_message" || toolName == "send_sms") {
                    contact == null || contact !in trustedAutoSendContacts
                } else {
                    true
                }
            }
            RiskLevel.LOW -> false
        }
    }

    fun markContactTrustedForAutoSend(contact: String) {
        trustedAutoSendContacts.add(contact)
    }

    fun revokeTrustedAutoSend(contact: String) {
        trustedAutoSendContacts.remove(contact)
    }

    fun confirmationPrompt(toolName: String, arguments: Map<String, String>): String = when (toolName) {
        "make_phone_call" -> "Call ${arguments["contact"] ?: "this contact"}?"
        "send_sms" -> "Send this SMS to ${arguments["contact"]}: \"${arguments["message"]}\"?"
        "send_whatsapp_message" -> "Send this WhatsApp message to ${arguments["contact"]}: \"${arguments["message"]}\"?"
        else -> "Go ahead with this action?"
    }
}
