package dev.breadcrumb.app

import android.net.Uri

internal data class NotebookState(
    val entries: List<NotebookEntry>,
    val notebookUri: Uri?,
    val loading: Boolean,
    val folderError: String?,
    val deleteEntry: (NotebookEntry) -> Unit,
    val refresh: () -> Unit,
    val open: (NotebookEntry) -> Unit,
    val navigate: (String) -> Unit,
)

internal fun NotebookViewModel.notebookState() =
    NotebookState(
        entries = this.entries,
        notebookUri = this.notebookUri,
        loading = this.loading,
        folderError = this.folderError,
        deleteEntry = this::deleteEntry,
        refresh = this::refresh,
        open = this::open,
        navigate = this::navigate,
    )

internal data class EditorState(
    val notebookUri: Uri?,
    val loading: Boolean,
    val saving: Boolean,
    val reading: Boolean,
    val solutionUris: List<Uri>,
    val editingId: String?,
    val scanUri: Uri?,
    val query: String,
    val hasSearchKey: Boolean,
    val searching: Boolean,
    val status: String,
    val matches: List<ProblemMatch>,
    val url: String,
    val title: String,
    val site: ProblemSite,
    val save: (List<Uri>) -> Unit,
    val updateQuery: (String) -> Unit,
    val findMatches: () -> Unit,
    val select: (ProblemMatch) -> Unit,
    val updateUrl: (String) -> Unit,
    val cleanUrl: () -> Unit,
    val updateTitle: (String) -> Unit,
    val updateSite: (ProblemSite) -> Unit,
    val removePhoto: (Uri) -> Unit,
)

internal fun NotebookViewModel.editorState() =
    EditorState(
        notebookUri = this.notebookUri,
        loading = this.loading,
        saving = this.saving,
        reading = this.reading,
        solutionUris = this.solutionUris,
        editingId = this.editingId,
        scanUri = this.scanUri,
        query = this.query,
        hasSearchKey = this.hasSearchKey,
        searching = this.searching,
        status = this.status,
        matches = this.matches,
        url = this.url,
        title = this.title,
        site = this.site,
        save = this::save,
        updateQuery = this::updateQuery,
        findMatches = this::findMatches,
        select = this::select,
        updateUrl = this::updateUrl,
        cleanUrl = this::cleanUrl,
        updateTitle = this::updateTitle,
        updateSite = this::updateSite,
        removePhoto = this::removePhoto,
    )

internal data class DetailState(
    val loading: Boolean,
    val saving: Boolean,
    val selectedEntry: NotebookEntry?,
    val deleteEntry: (NotebookEntry) -> Unit,
    val changeEntrySite: (NotebookEntry, ProblemSite) -> Unit,
)

internal fun NotebookViewModel.detailState() =
    DetailState(
        loading = this.loading,
        saving = this.saving,
        selectedEntry = this.selectedEntry,
        deleteEntry = this::deleteEntry,
        changeEntrySite = this::changeEntrySite,
    )

internal data class SettingsState(
    val notebookUri: Uri?,
    val loading: Boolean,
    val saving: Boolean,
    val hasSearchKey: Boolean,
    val notebookName: String,
    val saveSearchKey: (String) -> Unit,
    val showTutorial: () -> Unit,
)

internal fun NotebookViewModel.settingsState() =
    SettingsState(
        notebookUri = this.notebookUri,
        loading = this.loading,
        saving = this.saving,
        hasSearchKey = this.hasSearchKey,
        notebookName = this.notebookName,
        saveSearchKey = this::saveSearchKey,
        showTutorial = this::showTutorial,
    )
