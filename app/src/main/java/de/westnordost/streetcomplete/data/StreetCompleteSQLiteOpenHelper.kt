package de.westnordost.streetcomplete.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import de.westnordost.streetcomplete.data.download.tiles.DownloadedTilesTable
import de.westnordost.streetcomplete.data.notifications.NewUserAchievementsTable
import de.westnordost.streetcomplete.data.osm.elementgeometry.ElementGeometryTable
import de.westnordost.streetcomplete.data.osm.mapdata.NodeTable
import de.westnordost.streetcomplete.data.osm.mapdata.RelationTable
import de.westnordost.streetcomplete.data.osm.mapdata.WayTable
import de.westnordost.streetcomplete.data.osm.osmquest.OsmQuestTable
import de.westnordost.streetcomplete.data.osm.osmquest.undo.UndoOsmQuestTable
import de.westnordost.streetcomplete.data.osm.splitway.OsmQuestSplitWayTable
import de.westnordost.streetcomplete.data.osm.upload.changesets.OpenChangesetsTable
import de.westnordost.streetcomplete.data.osmnotes.NoteTable
import de.westnordost.streetcomplete.data.osmnotes.createnotes.CreateNoteTable
import de.westnordost.streetcomplete.data.osmnotes.notequests.OsmNoteQuestTable
import de.westnordost.streetcomplete.data.user.CountryStatisticsTable
import de.westnordost.streetcomplete.data.user.QuestStatisticsTable
import de.westnordost.streetcomplete.data.user.achievements.UserAchievementsTable
import de.westnordost.streetcomplete.data.user.achievements.UserLinksTable
import de.westnordost.streetcomplete.data.visiblequests.QuestVisibilityTable
import javax.inject.Singleton

@Singleton class StreetCompleteSQLiteOpenHelper(context: Context, dbName: String) :
    SQLiteOpenHelper(context, dbName, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(ElementGeometryTable.CREATE)
        db.execSQL(OsmQuestTable.CREATE)

        db.execSQL(UndoOsmQuestTable.CREATE)

        db.execSQL(NodeTable.CREATE)
        db.execSQL(WayTable.CREATE)
        db.execSQL(RelationTable.CREATE)

        db.execSQL(NoteTable.CREATE)
        db.execSQL(OsmNoteQuestTable.CREATE)
        db.execSQL(CreateNoteTable.CREATE)

        db.execSQL(QuestStatisticsTable.CREATE)
        db.execSQL(CountryStatisticsTable.CREATE)
        db.execSQL(UserAchievementsTable.CREATE)
        db.execSQL(UserLinksTable.CREATE)
        db.execSQL(NewUserAchievementsTable.CREATE)

        db.execSQL(DownloadedTilesTable.CREATE)

        db.execSQL(OsmQuestTable.CREATE_VIEW)
        db.execSQL(UndoOsmQuestTable.MERGED_VIEW_CREATE)
        db.execSQL(OsmNoteQuestTable.CREATE_VIEW)

        db.execSQL(OpenChangesetsTable.CREATE)

        db.execSQL(QuestVisibilityTable.CREATE)

        db.execSQL(OsmQuestSplitWayTable.CREATE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // for later changes to the DB
        // ...
    }
}

private const val DB_VERSION = 18
