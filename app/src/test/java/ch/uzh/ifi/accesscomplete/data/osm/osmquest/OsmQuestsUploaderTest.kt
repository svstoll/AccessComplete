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

package ch.uzh.ifi.accesscomplete.data.osm.osmquest

import de.westnordost.osmapi.map.data.Element
import de.westnordost.osmapi.map.data.OsmLatLon
import de.westnordost.osmapi.map.data.OsmNode
import ch.uzh.ifi.accesscomplete.any
import ch.uzh.ifi.accesscomplete.data.quest.QuestStatus
import ch.uzh.ifi.accesscomplete.data.osm.changes.StringMapChanges
import ch.uzh.ifi.accesscomplete.data.osm.changes.StringMapEntryAdd
import ch.uzh.ifi.accesscomplete.data.osm.elementgeometry.ElementPointGeometry
import ch.uzh.ifi.accesscomplete.data.osm.upload.changesets.OpenQuestChangesetsManager
import ch.uzh.ifi.accesscomplete.data.osm.upload.ChangesetConflictException
import ch.uzh.ifi.accesscomplete.data.osm.upload.ElementConflictException
import ch.uzh.ifi.accesscomplete.data.osm.upload.ElementDeletedException
import ch.uzh.ifi.accesscomplete.data.user.StatisticsUpdater
import ch.uzh.ifi.accesscomplete.mock
import ch.uzh.ifi.accesscomplete.on
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.*
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class OsmQuestsUploaderTest {
    private lateinit var osmQuestController: OsmQuestController
    private lateinit var changesetManager: OpenQuestChangesetsManager
    private lateinit var singleChangeUploader: SingleOsmElementTagChangesUploader
    private lateinit var statisticsUpdater: StatisticsUpdater
    private lateinit var elementUpdateController: OsmElementUpdateController
    private lateinit var uploader: OsmQuestsUploader

    @Before fun setUp() {
        osmQuestController = mock()
        changesetManager = mock()
        singleChangeUploader = mock()
        statisticsUpdater = mock()
        elementUpdateController = mock()
        uploader = OsmQuestsUploader(changesetManager, elementUpdateController,
            osmQuestController, singleChangeUploader, statisticsUpdater)
    }

    @Test fun `cancel upload works`() {
        uploader.upload(AtomicBoolean(true))
        verifyZeroInteractions(elementUpdateController, changesetManager, singleChangeUploader, statisticsUpdater, osmQuestController)
    }

    @Test fun `catches ElementConflict exception`() {
        on(osmQuestController.getAllAnswered()).thenReturn(listOf(createQuest()))
        on(singleChangeUploader.upload(anyLong(), any(), any()))
            .thenThrow(ElementConflictException())
        on(elementUpdateController.get(any(), anyLong())).thenReturn(mock())

        uploader.upload(AtomicBoolean(false))

        // will not throw ElementConflictException
    }

    @Test fun `discard if element was deleted`() {
        val q = createQuest()
        on(osmQuestController.getAllAnswered()).thenReturn(listOf(q))
        on(singleChangeUploader.upload(anyLong(), any(), any()))
            .thenThrow(ElementDeletedException())
        on(elementUpdateController.get(any(), anyLong())).thenReturn(mock())

        uploader.uploadedChangeListener = mock()
        uploader.upload(AtomicBoolean(false))

        verify(uploader.uploadedChangeListener)?.onDiscarded(q.osmElementQuestType.javaClass.simpleName, q.position)
        verify(elementUpdateController).delete(any(), anyLong())
    }

    @Test fun `catches ChangesetConflictException exception and tries again once`() {
        on(osmQuestController.getAllAnswered()).thenReturn(listOf(createQuest()))
        on(singleChangeUploader.upload(anyLong(), any(), any()))
            .thenThrow(ChangesetConflictException())
            .thenReturn(createElement())
        on(elementUpdateController.get(any(), anyLong())).thenReturn(mock())

        uploader.upload(AtomicBoolean(false))

        // will not throw ChangesetConflictException but instead call single upload twice
        verify(changesetManager).getOrCreateChangeset(any(), any())
        verify(changesetManager).createChangeset(any(), any())
        verify(singleChangeUploader, times(2)).upload(anyLong(), any(), any())
    }

    @Test fun `close each uploaded quest in local DB and call listener`() {
        val quests = listOf(createQuest(), createQuest())

        on(osmQuestController.getAllAnswered()).thenReturn(quests)
        on(singleChangeUploader.upload(anyLong(), any(), any())).thenReturn(createElement())
        on(elementUpdateController.get(any(), anyLong())).thenReturn(mock())

        uploader.uploadedChangeListener = mock()
        uploader.upload(AtomicBoolean(false))

        verify(osmQuestController, times(2)).success(any())
        verify(uploader.uploadedChangeListener, times(2))?.onUploaded(any(), any())
        verify(elementUpdateController, times(2)).update(any(), isNull())
        verify(statisticsUpdater, times(2)).addOne(any(), any())
    }

    @Test fun `delete each unsuccessful upload from local DB and call listener`() {
        val quests = listOf(createQuest(), createQuest())

        on(osmQuestController.getAllAnswered()).thenReturn(quests)
        on(singleChangeUploader.upload(anyLong(), any(), any()))
            .thenThrow(ElementConflictException())
        on(elementUpdateController.get(any(), anyLong())).thenReturn(mock())

        uploader.uploadedChangeListener = mock()
        uploader.upload(AtomicBoolean(false))

        verify(osmQuestController, times(2)).fail(any())
        verify(uploader.uploadedChangeListener,times(2))?.onDiscarded(any(), any())
        verify(elementUpdateController, times(2)).get(any(), anyLong())
        verifyNoMoreInteractions(elementUpdateController)
        verifyZeroInteractions(statisticsUpdater)
    }

    @Test fun `delete unreferenced elements and clean metadata at the end`() {
        val quest = createQuest()

        on(osmQuestController.getAllAnswered()).thenReturn(listOf(quest))
        on(singleChangeUploader.upload(anyLong(), any(), any())).thenReturn(createElement())
        on(elementUpdateController.get(any(), anyLong())).thenReturn(mock())

        uploader.upload(AtomicBoolean(false))

        verify(quest.osmElementQuestType).cleanMetadata()
    }
}

private fun createQuest() : OsmQuest {
    val changes = StringMapChanges(listOf(StringMapEntryAdd("surface","asphalt")))
    val geometry = ElementPointGeometry(OsmLatLon(0.0, 0.0))
    return OsmQuest(1L, mock(), Element.Type.NODE, 1L, QuestStatus.ANSWERED, changes, "survey",
            Date(), geometry)
}

private fun createElement() = OsmNode(1,1,OsmLatLon(0.0,0.0),null)
