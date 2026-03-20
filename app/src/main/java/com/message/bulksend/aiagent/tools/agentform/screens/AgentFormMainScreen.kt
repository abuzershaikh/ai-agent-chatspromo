package com.message.bulksend.aiagent.tools.agentform.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier

private enum class AgentFormTab(
        val title: String,
        val subtitle: String,
        val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    TEMPLATES("My Templates", "Manage saved forms", Icons.Default.Description),
    RESPONSES("Responses", "Track submissions", Icons.Default.Inbox),
    AGENTFORM("AgentForm", "Create and share quickly", Icons.Default.Dashboard)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentFormMainScreen(
        onBack: () -> Unit,
        onCreateNew: () -> Unit,
        onEdit: (String) -> Unit,
        ownerUid: String,
        ownerPhone: String
) {
    var selectedTab by rememberSaveable { mutableStateOf(AgentFormTab.TEMPLATES.name) }
    val currentTab = AgentFormTab.valueOf(selectedTab)

    Scaffold(
            topBar = {
                TopAppBar(
                        title = {
                            Column {
                                Text(currentTab.title, style = MaterialTheme.typography.titleLarge)
                                Text(
                                        currentTab.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                )
            },
            bottomBar = {
                NavigationBar {
                    AgentFormTab.values().forEach { tab ->
                        NavigationBarItem(
                                selected = tab == currentTab,
                                onClick = { selectedTab = tab.name },
                                icon = { Icon(tab.icon, contentDescription = tab.title) },
                                label = { Text(tab.title) }
                        )
                    }
                }
            },
            floatingActionButton = {
                if (currentTab != AgentFormTab.RESPONSES) {
                    ExtendedFloatingActionButton(
                            onClick = onCreateNew,
                            icon = { Icon(Icons.Default.Add, contentDescription = null) },
                            text = { Text("Create Form") }
                    )
                }
            }
    ) { paddingValues ->
        val contentModifier = Modifier.padding(paddingValues)
        when (currentTab) {
            AgentFormTab.TEMPLATES -> {
                AgentFormTemplatesTabScreen(
                        modifier = contentModifier,
                        onCreateNew = onCreateNew,
                        onEdit = onEdit,
                        ownerUid = ownerUid,
                        ownerPhone = ownerPhone
                )
            }
            AgentFormTab.RESPONSES -> {
                AgentFormResponsesScreen(
                        modifier = contentModifier,
                        ownerUid = ownerUid,
                        ownerPhone = ownerPhone
                )
            }
            AgentFormTab.AGENTFORM -> {
                AgentFormOverviewScreen(
                        modifier = contentModifier,
                        onCreateNew = onCreateNew
                )
            }
        }
    }
}
