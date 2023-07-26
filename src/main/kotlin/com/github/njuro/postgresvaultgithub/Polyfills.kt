package com.github.njuro.postgresvaultgithub

import com.intellij.uiDesigner.core.GridConstraints
import java.awt.Dimension

fun createLabelConstraints(row: Int, col: Int, width: Double): GridConstraints = createConstraints(
    row = row,
    col = col,
    colSpan = 1,
    anchor = 0,
    fill = 3,
    prefWidth = width.toInt(),
    rubber = false
)

fun createSimpleConstraints(row: Int, col: Int, colSpan: Int): GridConstraints = createConstraints(
    row = row,
    col = col,
    colSpan = colSpan,
    anchor = 0,
    fill = 1,
    prefWidth = -1,
    rubber = true
)

private fun createConstraints(
    row: Int,
    col: Int,
    rowSpan: Int = 1,
    colSpan: Int,
    anchor: Int,
    fill: Int,
    prefWidth: Int,
    rubber: Boolean,
    vrubber: Boolean = false
): GridConstraints {
    val nonPref = Dimension(-1, -1)
    val pref = Dimension(if (prefWidth == -1) 100 else prefWidth, -1)
    return GridConstraints(
        row,
        col,
        rowSpan,
        colSpan,
        anchor,
        fill,
        getPolicy(rubber),
        getPolicy(vrubber),
        nonPref,
        pref,
        nonPref,
        0,
        true
    )
}

private fun getPolicy(rubber: Boolean): Int = if (rubber) 7 else 0


