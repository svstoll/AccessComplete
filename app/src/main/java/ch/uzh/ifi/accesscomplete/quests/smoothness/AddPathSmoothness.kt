/*
 * AccessComplete, an easy to use editor of accessibility related
 * OpenStreetMap data for Android.  This program is a fork of
 * StreetComplete (https://github.com/westnordost/StreetComplete).
 *
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

package ch.uzh.ifi.accesscomplete.quests.smoothness

import ch.uzh.ifi.accesscomplete.R
import ch.uzh.ifi.accesscomplete.data.elementfilter.ElementFilterExpression
import ch.uzh.ifi.accesscomplete.data.elementfilter.toElementFilterExpression

class AddPathSmoothness : AbstractAddSmoothnessQuestType() {

    private val baseExpression by lazy {
        """
            ways with (
              highway = footway
              or (highway ~ path|cycleway|bridleway and foot != no)
            )
            and segregated != yes
            and footway != crossing
            and !level
            and (!conveying or conveying = no)
            and (!indoor or indoor = no)
            and (!area or area = no)
        """.toElementFilterExpression()
    }

    override val icon = R.drawable.ic_quest_smoothness_path

    override fun getTitle(tags: Map<String, String>) = R.string.quest_smoothness_path_title

    override fun getBaseFilterExpression(): ElementFilterExpression = baseExpression

    // The ways provided in the baseExpression should not have sidewalk tags.
    override fun supportTaggingBySidewalkSide(): Boolean = false
}
