package com.suna.player.playback

/**
 * Where a "Play next" song should be inserted.
 *
 * Songs added with "Play next" carry a flag. A new one goes after the unbroken run of flagged
 * songs that directly follows the current song, so several "Play next" picks play in the order
 * they were picked. The rule is stateless: it survives the app or the service being restarted,
 * and removing or reordering queue items needs no bookkeeping.
 */
fun playNextInsertIndex(
    currentIndex: Int,
    itemCount: Int,
    isPlayNext: (index: Int) -> Boolean,
): Int {
    if (itemCount <= 0) return 0
    var index = (currentIndex + 1).coerceIn(0, itemCount)
    while (index < itemCount && isPlayNext(index)) index++
    return index
}
