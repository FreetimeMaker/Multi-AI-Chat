package com.freetime.maic.activities

import com.freetime.maic.settings.AppSettingsState
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class UpdateNotificationActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val settings = AppSettingsState.instance
        val currentVersion = PluginManagerCore.getPlugin(PluginId.getId("com.freetime.maic"))?.version ?: return
        
        // Wenn lastVersion leer ist, ist dies die Erstinstallation. 
        // Wir speichern die Version einfach, ohne eine Benachrichtigung zu zeigen.
        if (settings.lastVersion.isEmpty()) {
            settings.lastVersion = currentVersion
            return
        }
        
        if (settings.lastVersion != currentVersion) {
            showUpdateNotification(project, currentVersion)
            settings.lastVersion = currentVersion
        }
    }

    private fun showUpdateNotification(project: Project, version: String) {
        val content = """
            Multi AI Chat has been updated to $version!<br><br>
            <b>What's New:</b><br>
            - Added support for Google Gemini 2.5 Pro and Flash.<br>
            - Changed the Error instead of 404 to a real message.
        """.trimIndent()

        NotificationGroupManager.getInstance()
            .getNotificationGroup("Multi AI Notifications")
            .createNotification("Multi AI Chat Updated", content, NotificationType.INFORMATION)
            .notify(project)
    }
}
