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

package ch.uzh.ifi.accesscomplete.quests

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.PopupMenu
import androidx.annotation.AnyThread
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.text.parseAsHtml
import androidx.core.view.isGone
import com.google.android.flexbox.FlexboxLayout
import de.westnordost.osmapi.map.data.OsmElement
import de.westnordost.osmapi.map.data.Way
import de.westnordost.osmfeatures.FeatureDictionary
import ch.uzh.ifi.accesscomplete.Injector
import ch.uzh.ifi.accesscomplete.R
import ch.uzh.ifi.accesscomplete.data.meta.CountryInfo
import ch.uzh.ifi.accesscomplete.data.meta.CountryInfos
import ch.uzh.ifi.accesscomplete.data.osm.elementgeometry.ElementGeometry
import ch.uzh.ifi.accesscomplete.data.osm.osmquest.OsmElementQuestType
import ch.uzh.ifi.accesscomplete.data.quest.Quest
import ch.uzh.ifi.accesscomplete.data.quest.QuestGroup
import ch.uzh.ifi.accesscomplete.data.quest.QuestType
import ch.uzh.ifi.accesscomplete.data.quest.QuestTypeRegistry
import ch.uzh.ifi.accesscomplete.ktx.isArea
import kotlinx.android.synthetic.main.fragment_quest_answer.*
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.FutureTask
import javax.inject.Inject

/** Abstract base class for any bottom sheet with which the user answers a specific quest(ion)  */
abstract class AbstractQuestAnswerFragment<T> : AbstractBottomSheetFragment(), IsShowingQuestDetails {

    // dependencies
    private val countryInfos: CountryInfos
    private val questTypeRegistry: QuestTypeRegistry
    private val featureDictionaryFuture: FutureTask<FeatureDictionary>

    private var _countryInfo: CountryInfo? = null // lazy but resettable because based on lateinit var
        get() {
            if(field == null) {
                val latLon = elementGeometry.center
                field = countryInfos.get(latLon.longitude, latLon.latitude)
            }
            return field
        }
    protected val countryInfo get() = _countryInfo!!

    // views
    private lateinit var content: ViewGroup
    private lateinit var buttonPanel: FlexboxLayout
    private lateinit var otherAnswersButton: Button

    // passed in parameters
    override var questId: Long = 0L
    override var questGroup: QuestGroup = QuestGroup.OSM
    protected lateinit var elementGeometry: ElementGeometry private set
    private lateinit var questType: QuestType<T>
    private var initialMapRotation = 0f
    private var initialMapTilt = 0f
    protected var osmElement: OsmElement? = null
        private set

    private var currentContext = WeakReference<Context>(null)
    private var currentCountryContext: ContextWrapper? = null

    private val englishResources: Resources
        get() {
            val conf = Configuration(resources.configuration)
            conf.setLocale(Locale.ENGLISH)
            val localizedContext = super.requireContext().createConfigurationContext(conf)
            return localizedContext.resources
        }

    private var startedOnce = false

    // overridable by child classes
    open val contentLayoutResId: Int? = null
    open val buttonsResId: Int? = null
    open val otherAnswers = listOf<OtherAnswer>()
    open val contentPadding = true
    open val defaultExpanded = true

    interface Listener {

        enum class SidewalkSide{
            LEFT, RIGHT
        }

        /** Called when the user answered the quest with the given id. What is in the bundle, is up to
         * the dialog with which the quest was answered  */
        fun onAnsweredQuest(questId: Long, group: QuestGroup, answer: Any)

        /** Called when the user chose to leave a note instead  */
        fun onComposeNote(questId: Long, group: QuestGroup, questTitle: String)

        /** Called when the user chose to split the way  */
        fun onSplitWay(osmQuestId: Long)

        /** Called when the user chose to skip the quest  */
        fun onSkippedQuest(questId: Long, group: QuestGroup)

        fun onHighlightSidewalkSide(questId: Long, group: QuestGroup, side: SidewalkSide)
    }
    private val listener: Listener? get() = parentFragment as? Listener ?: activity as? Listener

    init {
        val fields = InjectedFields()
        Injector.applicationComponent.inject(fields)
        countryInfos = fields.countryInfos
        featureDictionaryFuture = fields.featureDictionaryFuture
        questTypeRegistry = fields.questTypeRegistry
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val args = requireArguments()
        questId = args.getLong(ARG_QUEST_ID)
        questGroup = QuestGroup.valueOf(args.getString(ARG_QUEST_GROUP)!!)
        osmElement = args.getSerializable(ARG_ELEMENT) as OsmElement?
        elementGeometry = args.getSerializable(ARG_GEOMETRY) as ElementGeometry
        questType = questTypeRegistry.getByName(args.getString(ARG_QUESTTYPE)!!) as QuestType<T>
        initialMapRotation = args.getFloat(ARG_MAP_ROTATION)
        initialMapTilt = args.getFloat(ARG_MAP_TILT)
        _countryInfo = null // reset lazy field
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_quest_answer, container, false)
        content = view.findViewById(R.id.content)
        buttonPanel = view.findViewById(R.id.buttonPanel)
        otherAnswersButton = buttonPanel.findViewById(R.id.otherAnswersButton)

        contentLayoutResId?.let { setContentView(it) }
        buttonsResId?.let { setButtonsView(it) }
        if(!contentPadding) content.setPadding(0,0,0,0)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        titleLabel.text = resources.getHtmlQuestTitle(questType, osmElement, featureDictionaryFuture)
        titleLabel.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)

        val levelLabelText = getLocationLabelText()
        locationLabel.isGone = levelLabelText == null
        if (levelLabelText != null) {
            locationLabel.text = levelLabelText
        }

        // no content? -> hide the content container
        if (content.childCount == 0) {
            content.visibility = View.GONE
        }

        if (defaultExpanded) expand()
    }

    private fun assembleOtherAnswers() : List<OtherAnswer> {
        val answers = mutableListOf<OtherAnswer>()

        val cantSay = OtherAnswer(R.string.quest_generic_answer_notApplicable) { onClickCantSay() }
        answers.add(cantSay)

        val isSplitWayEnabled = (questType as? OsmElementQuestType)?.isSplitWayEnabled == true
        if (isSplitWayEnabled) {
            val way = osmElement as? Way
            if (way != null) {
                /* splitting up a closed roundabout can be very complex if it is part of a route
               relation, so it is not supported
               https://wiki.openstreetmap.org/wiki/Relation:route#Bus_routes_and_roundabouts
            */
                val isClosedRoundabout = way.nodeIds.firstOrNull() == way.nodeIds.lastOrNull() &&
                    way.tags?.get("junction") == "roundabout"
                if (!isClosedRoundabout && !way.isArea()) {
                    val splitWay = OtherAnswer(R.string.quest_generic_answer_differs_along_the_way) {
                        onClickSplitWayAnswer()
                    }
                    answers.add(splitWay)
                }
            }
        }

        answers.addAll(otherAnswers)
        answers.addAll(addOtherAnswersDynamically())
        return answers
    }

    protected open fun addOtherAnswersDynamically(): List<OtherAnswer> {
        return emptyList()
    }

    private fun showOtherAnswers() {
        val answers = assembleOtherAnswers()
        val popup = PopupMenu(requireContext(), otherAnswersButton)
        for (i in answers.indices) {
            val otherAnswer = answers[i]
            val order = answers.size - i
            popup.menu.add(Menu.NONE, i, order, otherAnswer.titleResourceId)
        }
        popup.show()

        popup.setOnMenuItemClickListener { item ->
            answers[item.itemId].action()
            true
        }
    }

    private fun getLocationLabelText(): CharSequence? {
        // prefer to show the level if both are present because it is a more precise indication
        // where it is supposed to be
        return getLevelLabelText() ?: getHouseNumberLabelText()
    }

    private fun getLevelLabelText(): CharSequence? {
        val tags = osmElement?.tags ?: return null
        /* prefer addr:floor etc. over level as level is rather an index than how the floor is
           denominated in the building and thus may (sometimes) not coincide with it. E.g.
           addr:floor may be "M" while level is "2" */
        val level = tags["addr:floor"] ?: tags["level:ref"] ?: tags["level"]
        if (level != null) {
            return resources.getString(R.string.on_level, level)
        }
        val tunnel = tags["tunnel"]
        if(tunnel != null && tunnel == "yes" || tags["location"] == "underground") {
            return resources.getString(R.string.underground)
        }
        return null
    }

    private fun getHouseNumberLabelText(): CharSequence? {
        val tags = osmElement?.tags ?: return null

        val houseName = tags["addr:housename"]
        val conscriptionNumber = tags["addr:conscriptionnumber"]
        val streetNumber = tags["addr:streetnumber"]
        val houseNumber = tags["addr:housenumber"]

        if (houseName != null) {
            return resources.getString(R.string.at_housename, "<i>" + Html.escapeHtml(houseName) + "</i>")
                .parseAsHtml()
        }
        if (conscriptionNumber != null) {
            return if (streetNumber != null) {
                resources.getString(R.string.at_conscription_and_street_number, conscriptionNumber, streetNumber)
            } else {
                resources.getString(R.string.at_conscription_number, conscriptionNumber)
            }
        }
        if (houseNumber != null) {
            return resources.getString(R.string.at_housenumber, houseNumber)
        }
        return null
    }

    override fun onStart() {
        super.onStart()
        if(!startedOnce) {
            onMapOrientation(initialMapRotation, initialMapTilt)
            startedOnce = true
        }

        val answers = assembleOtherAnswers()
        if (answers.size == 1) {
            otherAnswersButton.setText(answers.first().titleResourceId)
            otherAnswersButton.setOnClickListener { answers.first().action() }
        } else {
            otherAnswersButton.setText(R.string.quest_generic_otherAnswers)
            otherAnswersButton.setOnClickListener { showOtherAnswers() }
        }
    }

    /** Note: Due to Android architecture limitations, a layout inflater based on this ContextWrapper
     * will not resolve any resources specified in the XML according to MCC  */
    override fun onGetLayoutInflater(savedInstanceState: Bundle?): LayoutInflater {
        // will always return a layout inflater for the current country
        return super.onGetLayoutInflater(savedInstanceState).cloneInContext(context)
    }

    override fun getContext(): Context? {
        val context = super.getContext()
        if (currentContext.get() !== context) {
            currentContext = WeakReference<Context>(context)
            currentCountryContext = if (context != null) createCurrentCountryContextWrapper(context) else null
        }
        return currentCountryContext
    }

    private fun createCurrentCountryContextWrapper(context: Context): ContextWrapper {
        val conf = Configuration(context.resources.configuration)
        conf.mcc = countryInfo.mobileCountryCode ?: 0
        val res = context.createConfigurationContext(conf).resources
        return object : ContextWrapper(context) {
            override fun getResources(): Resources {
                return res
            }
        }
    }

    protected fun onClickCantSay() {
        context?.let { AlertDialog.Builder(it)
            .setTitle(R.string.quest_leave_new_note_title)
            .setMessage(R.string.quest_leave_new_note_description)
            .setNegativeButton(R.string.quest_leave_new_note_no) { _, _ -> skipQuest() }
            .setPositiveButton(R.string.quest_leave_new_note_yes) { _, _ -> composeNote() }
            .show()
        }
    }

    protected fun composeNote() {
        val questTitle = englishResources.getQuestTitle(questType, osmElement, featureDictionaryFuture)
        listener?.onComposeNote(questId, questGroup, questTitle)
    }

    private fun onClickSplitWayAnswer() {
        context?.let { AlertDialog.Builder(it)
            .setMessage(R.string.quest_split_way_description)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (questGroup != QuestGroup.OSM) throw IllegalStateException()
                listener?.onSplitWay(questId)
            }
            .show()
        }
    }

    protected fun applyAnswer(data: T) {
        listener?.onAnsweredQuest(questId, questGroup, data as Any)
    }

    protected fun skipQuest() {
        listener?.onSkippedQuest(questId, questGroup)
    }

    protected fun setContentView(resourceId: Int): View {
        if (content.childCount > 0) {
            content.removeAllViews()
        }
        content.visibility = View.VISIBLE
        return layoutInflater.inflate(resourceId, content)
    }

    protected fun setNoContentPadding() {
        content.setPadding(0, 0, 0, 0)
    }

    protected fun setButtonsView(resourceId: Int) {
        otherAnswersButton.layoutParams = FlexboxLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        removeButtonsView()
        activity?.layoutInflater?.inflate(resourceId, buttonPanel)
    }

    protected fun removeButtonsView() {
        if (buttonPanel.childCount > 1) {
            buttonPanel.removeViews(1, buttonPanel.childCount - 1)
        }
    }

    @AnyThread open fun onMapOrientation(rotation: Float, tilt: Float) {
        // default empty implementation
    }

    class InjectedFields {
        @Inject internal lateinit var countryInfos: CountryInfos
        @Inject internal lateinit var questTypeRegistry: QuestTypeRegistry
        @Inject internal lateinit var featureDictionaryFuture: FutureTask<FeatureDictionary>
    }

    companion object {
        private const val ARG_QUEST_ID = "questId"
        private const val ARG_QUEST_GROUP = "questGroup"
        private const val ARG_ELEMENT = "element"
        private const val ARG_GEOMETRY = "geometry"
        private const val ARG_QUESTTYPE = "quest_type"
        private const val ARG_MAP_ROTATION = "map_rotation"
        private const val ARG_MAP_TILT = "map_tilt"

        fun createArguments(quest: Quest, group: QuestGroup, element: OsmElement?, rotation: Float, tilt: Float) = bundleOf(
            ARG_QUEST_ID to quest.id!!,
            ARG_QUEST_GROUP to group.name,
            ARG_ELEMENT to element,
            ARG_GEOMETRY to quest.geometry,
            ARG_QUESTTYPE to quest.type.javaClass.simpleName,
            ARG_MAP_ROTATION to rotation,
            ARG_MAP_TILT to tilt
        )
    }
}

data class OtherAnswer(val titleResourceId: Int, val action: () -> Unit)
