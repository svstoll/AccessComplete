/*
 * AccessComplete, an easy to use editor of accessibility related
 * OpenStreetMap data for Android.  This program is a fork of
 * StreetComplete (https://github.com/westnordost/StreetComplete).
 *
 * Copyright (C) 2016-2020 Tobias Zwick and contributors (StreetComplete authors)
 * Copyright (C) 2020 Sven Stoll (AccessComplete author)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ch.uzh.ifi.accesscomplete.controls

import android.content.SharedPreferences
import androidx.fragment.app.Fragment
import ch.uzh.ifi.accesscomplete.Injector
import ch.uzh.ifi.accesscomplete.Prefs
import ch.uzh.ifi.accesscomplete.R
import ch.uzh.ifi.accesscomplete.data.quest.UnsyncedChangesCountListener
import ch.uzh.ifi.accesscomplete.data.quest.UnsyncedChangesCountSource
import ch.uzh.ifi.accesscomplete.data.upload.UploadProgressListener
import ch.uzh.ifi.accesscomplete.data.upload.UploadProgressSource
import ch.uzh.ifi.accesscomplete.data.user.QuestStatisticsDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Fragment that shows (and hides) the undo button, based on whether there is anything to undo */
class AnswersCounterFragment : Fragment(R.layout.fragment_answers_counter),
    CoroutineScope by CoroutineScope(Dispatchers.Main) {

    @Inject internal lateinit var uploadProgressSource: UploadProgressSource
    @Inject internal lateinit var prefs: SharedPreferences
    @Inject internal lateinit var questStatisticsDao: QuestStatisticsDao
    @Inject internal lateinit var unsyncedChangesCountSource: UnsyncedChangesCountSource

    private val answersCounterView get() = view as AnswersCounterView

    private val uploadProgressListener = object : UploadProgressListener {
        override fun onStarted() { launch(Dispatchers.Main) { updateProgress(true) } }
        override fun onFinished() { launch(Dispatchers.Main) { updateProgress(false) } }
    }

    private val unsyncedChangesCountListener = object : UnsyncedChangesCountListener {
        override fun onUnsyncedChangesCountIncreased() { launch(Dispatchers.Main) { updateCount(true) }}
        override fun onUnsyncedChangesCountDecreased() { launch(Dispatchers.Main) { updateCount(true) }}
    }

    private val questStatisticsListener = object : QuestStatisticsDao.Listener {
        override fun onAddedOne(questType: String) {
            launch(Dispatchers.Main) {
                answersCounterView.setUploadedCount(answersCounterView.uploadedCount + 1, true)
            }
        }
        override fun onSubtractedOne(questType: String) {
            launch(Dispatchers.Main) {
                launch(Dispatchers.Main) {
                    answersCounterView.setUploadedCount(answersCounterView.uploadedCount - 1, true)
                }
            }
        }
        override fun onReplacedAll() {
            launch(Dispatchers.Main) { updateCount(false) }
        }
    }

    /* --------------------------------------- Lifecycle ---------------------------------------- */

    init {
        Injector.applicationComponent.inject(this)
    }

    override fun onStart() {
        super.onStart()
        /* If autosync is on, the answers counter also shows the upload progress bar instead of
         *  upload button, and shows the uploaded + uploadable amount of quests.
         */
        updateProgress(uploadProgressSource.isUploadInProgress)
        updateCount(false)
        if (isAutosync) {
            uploadProgressSource.addUploadProgressListener(uploadProgressListener)
            unsyncedChangesCountSource.addListener(unsyncedChangesCountListener)
        }
        questStatisticsDao.addListener(questStatisticsListener)
    }

    override fun onStop() {
        super.onStop()
        uploadProgressSource.removeUploadProgressListener(uploadProgressListener)
        questStatisticsDao.removeListener(questStatisticsListener)
        unsyncedChangesCountSource.removeListener(unsyncedChangesCountListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineContext.cancel()
    }

    private val isAutosync: Boolean get() =
        Prefs.Autosync.valueOf(prefs.getString(Prefs.AUTOSYNC, "ON")!!) == Prefs.Autosync.ON

    private fun updateProgress(isUploadInProgress: Boolean) {
        answersCounterView.showProgress = isUploadInProgress && isAutosync
    }

    private fun updateCount(animated: Boolean) {
        /* if autosync is on, show the uploaded count + the to-be-uploaded count (but only those
           uploadables that will be part of the statistics, so no note stuff) */
        val amount = questStatisticsDao.getTotalAmount() + if (isAutosync) unsyncedChangesCountSource.questCount else 0
        answersCounterView.setUploadedCount(amount, animated)
    }
}
