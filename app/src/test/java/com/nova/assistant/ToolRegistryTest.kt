package com.nova.assistant

import com.nova.assistant.domain.tools.ToolRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRegistryTest {

    @Test
    fun `unknown tool is rejected`() {
        val result = ToolRegistry.validate("delete_all_data", emptyMap())
        assertTrue(result is ToolRegistry.ValidationResult.Rejected)
    }

    @Test
    fun `missing required arg is rejected`() {
        val result = ToolRegistry.validate("open_app", emptyMap())
        assertTrue(result is ToolRegistry.ValidationResult.Rejected)
    }

    @Test
    fun `valid call passes`() {
        val result = ToolRegistry.validate("open_app", mapOf("app_name" to "YouTube"))
        assertTrue(result is ToolRegistry.ValidationResult.Valid)
    }

    @Test
    fun `unknown argument is rejected`() {
        val result = ToolRegistry.validate("open_app", mapOf("app_name" to "YouTube", "evil_arg" to "x"))
        assertTrue(result is ToolRegistry.ValidationResult.Rejected)
    }
}
