package com.message.bulksend.autorespond.ai.ui.customai

internal object SheetAgentInfo {
    internal const val READ_SOURCE = "read_source"
    internal const val FOLDER_SELECTION = "folder_selection"
    internal const val READ_MAPPING = "read_mapping"
    internal const val FOLDER_FIELDS = "folder_fields"

    internal data class SectionInfo(
        val title: String,
        val description: String,
        val points: List<String>
    )

    private val infoBySection: Map<String, SectionInfo> =
        mapOf(
            READ_SOURCE to
                SectionInfo(
                    title = "Custom Read Source",
                    description = "This section controls where the AI agent reads customer context from.",
                    points =
                        listOf(
                            "The default agent sheet keeps running in the background.",
                            "When you select a custom folder, the AI agent can use those sheets for lookup during conversations.",
                            "Use this when you want a dedicated data source for one template."
                        )
                ),
            FOLDER_SELECTION to
                SectionInfo(
                    title = "Folder Selection",
                    description = "Select an existing custom folder or create a new one with a clear sheet structure.",
                    points =
                        listOf(
                            "Folder dropdown shows how many sheets already exist inside each folder.",
                            "Open TableSheet to add sheets and columns inside the selected folder.",
                            "Refresh after creating or editing sheets so the agent gets the latest structure.",
                            "No custom folder means the agent falls back to the default flow."
                        )
                ),
            READ_MAPPING to
                SectionInfo(
                    title = "Read Mapping",
                    description = "Define how the AI agent matches a customer to rows in your custom sheets.",
                    points =
                        listOf(
                            "Reference Sheet decides where match-field options are read from.",
                            "Match Field should be a single unique identifier like phone, email, or customer id.",
                            "Correct mapping helps the agent find the right row and answer with relevant data."
                        )
                ),
            FOLDER_FIELDS to
                SectionInfo(
                    title = "Folder Sheet Fields",
                    description = "View all sheets and available columns from the selected folder.",
                    points =
                        listOf(
                            "This is a quick audit panel for what the AI agent can read.",
                            "If expected columns are missing, add them in TableSheet and tap Refresh.",
                            "Clear column names improve matching and reduce wrong tool calls."
                        )
                )
        )

    internal fun get(section: String): SectionInfo {
        return infoBySection[section]
            ?: SectionInfo(
                title = "Sheet Agent Info",
                description = "Information is not available for this section yet.",
                points = emptyList()
            )
    }
}
