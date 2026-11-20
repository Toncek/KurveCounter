package com.motogeek.telemetry.domain.onboarding

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface TutorialDraftPersistence {
    fun readDraftJson(): String?
    fun writeDraftJson(rawJson: String)
    fun clearDraftJson()
}

class SharedPreferencesTutorialDraftPersistence(context: Context) : TutorialDraftPersistence {
    private val prefs = (context.applicationContext ?: context)
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun readDraftJson(): String? = prefs.getString(KEY_DRAFT_JSON, null)

    override fun writeDraftJson(rawJson: String) {
        prefs.edit().putString(KEY_DRAFT_JSON, rawJson).apply()
    }

    override fun clearDraftJson() {
        prefs.edit().remove(KEY_DRAFT_JSON).apply()
    }

    companion object {
        const val PREFS_NAME = "new_bike_tutorial_draft"
        const val KEY_DRAFT_JSON = "draft_json"
    }
}

class NewBikeTutorialDraftStore(
    private val persistence: TutorialDraftPersistence
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun loadDraft(): NewBikeTutorialDraft? {
        val raw = persistence.readDraftJson() ?: return null
        if (raw.isBlank()) return null
        return try {
            json.decodeFromString<NewBikeTutorialDraft>(raw)
        } catch (_: Exception) {
            null
        }
    }

    fun saveDraft(
        draft: NewBikeTutorialDraft,
        nowEpochMs: Long = System.currentTimeMillis()
    ): NewBikeTutorialDraft {
        val updated = draft.copy(lastAutosavedEpochMs = nowEpochMs)
        persistence.writeDraftJson(json.encodeToString(updated))
        return updated
    }

    fun clearDraft() {
        persistence.clearDraftJson()
    }
}
