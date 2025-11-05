package com.baksart.note2tex.data.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.baksart.note2tex.domain.model.AppTheme
import com.baksart.note2tex.data.repo.ExportFormat
import com.baksart.note2tex.domain.model.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ds by preferencesDataStore("note2tex_settings")

class AppSettingsStore(private val context: Context) {

    private val KEY_THEME = stringPreferencesKey("app_theme")
    private val KEY_DEFAULT_EXPORT = stringPreferencesKey("default_export_format")

    private val KEY_LANGUAGE = stringPreferencesKey("app_language")
    val themeFlow: Flow<AppTheme> =
        context.ds.data.map { prefs ->
            AppTheme.fromKey(prefs[KEY_THEME])
        }

    suspend fun setTheme(theme: AppTheme) {
        context.ds.edit { it[KEY_THEME] = theme.key }
    }

    val defaultExportFlow: Flow<ExportFormat> =
        context.ds.data.map { prefs ->
            when (prefs[KEY_DEFAULT_EXPORT]) {
                ExportFormat.LATEX.name -> ExportFormat.LATEX
                ExportFormat.DOCX.name  -> ExportFormat.DOCX
                ExportFormat.PDF.name   -> ExportFormat.PDF
                else                    -> ExportFormat.PDF
            }
        }

    suspend fun setDefaultExport(format: ExportFormat) {
        context.ds.edit { it[KEY_DEFAULT_EXPORT] = format.name }
    }

    val languageFlow: Flow<AppLanguage> =
        context.ds.data.map { prefs ->
            AppLanguage.fromTag(prefs[KEY_LANGUAGE])
        }

    suspend fun setLanguage(lang: AppLanguage) {
        context.ds.edit { it[KEY_LANGUAGE] = lang.tag }
    }
}
