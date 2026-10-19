package com.limersvp.ui.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.limersvp.R
import com.limersvp.feel.LocalReduceMotion
import com.limersvp.ui.theme.LocalPalette

/**
 * Teaches by doing: one short line for the next gesture, which moves on only when the person has
 * actually done it. Never covers the word; dims while reading.
 */
@Composable
fun TutorialCard(step: Int, playing: Boolean, tapMode: Boolean, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val reduce = LocalReduceMotion.current
    AnimatedVisibility(
        step >= 0,
        enter = if (reduce) fadeIn(tween(0)) else fadeIn(tween(220)) + scaleIn(spring(dampingRatio = 0.7f, stiffness = 500f), initialScale = 0.92f),
        exit = fadeOut(tween(if (reduce) 0 else 260)),
        modifier = modifier,
    ) {
        val alpha by animateFloatAsState(if (playing) 0.45f else 1f, tween(200), label = "tutorialDim")
        Row(
            Modifier
                .graphicsLayer { this.alpha = alpha }
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(p.raised)
                .padding(start = 18.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = { fadeIn(tween(if (reduce) 0 else 240)) togetherWith fadeOut(tween(if (reduce) 0 else 160)) },
                    label = "tutorialStep",
                ) { s ->
                    Text(
                        stringResource(lineFor(s, tapMode)), color = p.text, fontSize = 16.sp, lineHeight = 21.sp,
                        minLines = 2, maxLines = 3,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in Tutorial.READ until Tutorial.DONE) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (i < step) p.accent else if (i == step) p.dim else p.track)
                        )
                    }
                }
            }
            if (step < Tutorial.DONE) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(remember { MutableInteractionSource() }, null, onClick = onSkip)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(stringResource(R.string.tut_skip), color = p.dim, fontSize = 14.sp)
                }
            }
        }
    }
}

private fun lineFor(step: Int, tapMode: Boolean): Int = when (step) {
    Tutorial.READ -> if (tapMode) R.string.tut_read_tap else R.string.tut_read
    Tutorial.BACK -> R.string.tut_back
    Tutorial.SCRUB -> R.string.tut_scrub
    Tutorial.SPEED -> R.string.tut_speed
    else -> R.string.tut_done
}
