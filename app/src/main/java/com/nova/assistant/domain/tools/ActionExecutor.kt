package com.nova.assistant.domain.tools

import com.nova.assistant.domain.model.ActionResult
import com.nova.assistant.domain.model.ActionStatus
import com.nova.assistant.domain.model.ToolCall
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Each concrete handler executes ONE validated tool and returns a structured ActionResult.
 * Handlers must verify the real-world effect (e.g. check foreground package after open_app)
 * rather than assuming success — the assistant is never allowed to claim "Done" on a guess.
 */
fun interface ToolHandler {
    suspend fun execute(arguments: Map<String, String>): ActionResult
}

@Singleton
class ActionExecutor @Inject constructor(
    private val riskManager: ActionRiskManager
) {
    private val handlers = mutableMapOf<String, ToolHandler>()

    fun register(toolName: String, handler: ToolHandler) {
        handlers[toolName] = handler
    }

    suspend fun execute(call: ToolCall, confirmed: Boolean): ActionResult {
        val definition = ToolRegistry.get(call.tool)
            ?: return ActionResult(ActionStatus.NOT_SUPPORTED, "Nova doesn't have a '${call.tool}' capability.")

        if (!confirmed && riskManager.requiresConfirmation(call.tool, call.arguments)) {
            return ActionResult(
                status = ActionStatus.REQUIRES_CONFIRMATION,
                message = riskManager.confirmationPrompt(call.tool, call.arguments)
            )
        }

        val handler = handlers[call.tool]
            ?: return ActionResult(ActionStatus.NOT_SUPPORTED, "'${call.tool}' isn't wired to an executor yet.")

        return try {
            withTimeout(definition.timeoutMs) {
                handler.execute(call.arguments)
            }
        } catch (t: TimeoutCancellationException) {
            ActionResult(ActionStatus.TIMEOUT, "'${call.tool}' took too long and was cancelled.")
        } catch (t: SecurityException) {
            ActionResult(
                ActionStatus.REQUIRES_PERMISSION,
                "Nova needs a permission it doesn't have yet to do that.",
                permission = definition.requiredPermissions.firstOrNull()
            )
        } catch (t: Exception) {
            ActionResult(ActionStatus.FAILED, "That didn't work: ${t.message ?: "unknown error"}")
        }
    }
}
