package de.westnordost.streetcomplete.ktx

import de.westnordost.osmapi.map.data.LatLon

/** Return the first and last element of this list. If it contains only one element, just that one */
fun <E> List<E>.firstAndLast() = if (size == 1) listOf(first()) else listOf(first(), last())

/** Returns whether the collection contains any of the [elements] */
fun <E> Collection<E>.containsAny(elements: Collection<E>) = elements.any { contains(it) }

/**
 * Starting at [index] (exclusive), iterating the list in reverse, returns the first element that
 * matches the given [predicate], or `null` if no such element was found.
 */
inline fun <T> List<T>.findPrevious(index: Int, predicate: (T) -> Boolean): T? {
    val iterator = this.listIterator(index)
    while (iterator.hasPrevious()) {
        val element = iterator.previous()
        if (predicate(element)) return element
    }
    return null
}

/**
 * Starting at [index] (inclusive), iterating the list, returns the first element that
 * matches the given [predicate], or `null` if no such element was found.
 */
inline fun <T> List<T>.findNext(index: Int, predicate: (T) -> Boolean): T? {
    val iterator = this.listIterator(index)
    while (iterator.hasNext()) {
        val element = iterator.next()
        if (predicate(element)) return element
    }
    return null
}

/** Iterate through the given list of points in pairs, so [predicate] is called for every line */
inline fun Iterable<LatLon>.forEachLine(predicate: (first: LatLon, second: LatLon) -> Unit) {
    val it = iterator()
    if (!it.hasNext()) return
    var item1 = it.next()
    while (it.hasNext()) {
        val item2 = it.next()
        predicate(item1, item2)
        item1 = item2
    }
}

/** returns the index of the first element yielding the largest value of the given function or -1
 *  if there are no elements. Analogous to the maxBy extension function. */
inline fun <T, R : Comparable<R>> Iterable<T>.indexOfMaxBy(selector: (T) -> R): Int {
    val iterator = iterator()
    if (!iterator.hasNext()) return -1
    var indexOfMaxElem = 0
    var i = 0
    var maxValue = selector(iterator.next())
    while (iterator.hasNext()) {
        ++i
        val v = selector(iterator.next())
        if (maxValue < v) {
            indexOfMaxElem = i
            maxValue = v
        }
    }
    return indexOfMaxElem
}

inline fun <T> Iterable<T>.sumByFloat(selector: (T) -> Float): Float {
    var sum = 0f
    for (element in this) {
        sum += selector(element)
    }
    return sum
}

fun <T> Collection<T>.containsExactlyInAnyOrder(other: Collection<T>): Boolean =
    other.size == size && containsAll(other)
