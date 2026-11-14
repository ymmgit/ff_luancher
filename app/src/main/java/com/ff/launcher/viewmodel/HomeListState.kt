package com.ff.launcher.viewmodel

import com.ff.launcher.data.AppInfo

sealed interface HomeRow {
    data class AppRow(val app: AppInfo, val isFavorite: Boolean) : HomeRow
    data class Header(val label: String) : HomeRow
    data object Divider : HomeRow
}

/**
 * Everything the primary home screen renders, precomputed in the ViewModel.
 *
 * @param rows flattened LazyColumn content: favorites on top, then locale-collated
 *   sections (labels come from ICU in the device language) with a trailing `#` overflow.
 * @param sections jump targets for the wave scroller — one per populated section; the
 *   scroller renders exactly these labels (plus its settings node), never dead nodes.
 * @param favoriteCount number of pinned favorites; zero means the full list IS the home
 *   (the favorites-only collapsed mode has nothing to show).
 *
 * Lives in the viewmodel layer (not ui.*) so the dependency direction stays
 * ViewModel → data and UI → ViewModel, never the reverse.
 */
data class HomeListState(
    val rows: List<HomeRow> = emptyList(),
    val sections: List<Section> = emptyList(),
    val favoriteCount: Int = 0,
    val favoriteKeys: List<String> = emptyList(),
) {
    data class Section(val label: String, val rowIndex: Int)
}
