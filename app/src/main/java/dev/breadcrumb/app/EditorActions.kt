package dev.breadcrumb.app

internal data class EditorActions(
    val back: () -> Unit,
    val settings: () -> Unit,
    val scanCamera: () -> Unit,
    val scanImport: () -> Unit,
    val solutionCamera: () -> Unit,
    val solutionImport: () -> Unit,
    val chooseFolder: () -> Unit,
    val googleText: () -> Unit,
    val googleImage: () -> Unit,
    val openLink: (String) -> Unit,
)

