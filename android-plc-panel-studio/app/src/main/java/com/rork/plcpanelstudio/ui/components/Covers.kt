package com.rork.plcpanelstudio.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.plcpanelstudio.R
import com.rork.plcpanelstudio.ui.theme.Ink
import com.rork.plcpanelstudio.ui.theme.Line
import com.rork.plcpanelstudio.ui.theme.NameplateStyle
import com.rork.plcpanelstudio.ui.theme.SignalOrange
import com.rork.plcpanelstudio.ui.theme.TextLow
import com.rork.plcpanelstudio.ui.theme.TextMid

/** A selectable cover picture shown as the panel's face in the panels list. */
data class PanelCover(
    val id: String,
    val label: String,
    @DrawableRes val res: Int
)

/** Catalog of built-in cover pictures (bundled in res/drawable-nodpi). */
private const val COVER_ASPECT_RATIO = 1.5f

val PANEL_COVERS: List<PanelCover> = listOf(
    PanelCover("preparation", "Clay crusher", R.drawable.preparation),
    PanelCover("mouleuse", "Molding", R.drawable.mouleuse),
    PanelCover("multicoupeur", "Multi cutter", R.drawable.multicoupeur),
    PanelCover("dryer", "Dryer", R.drawable.dryer),
    PanelCover("kiln", "Tunnel kiln", R.drawable.kiln),
    PanelCover("fabric", "Kiln loading", R.drawable.fabric),
    PanelCover("emballage", "Unloading robot", R.drawable.emballage),
    PanelCover("packet", "Packaging", R.drawable.packet)
)

/** Resolves a stored cover id to a drawable, falling back to the app logo image. */
@DrawableRes
fun coverRes(coverId: String?): Int =
    when {
        coverId == "logo" -> R.drawable.logo
        PANEL_COVERS.any { it.id == coverId } -> PANEL_COVERS.first { it.id == coverId }.res
        else -> R.drawable.logo
    }

/** One selectable cover thumbnail used in the create dialog and panel settings. */
@Composable
fun CoverTile(
    item: PanelCover,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(100.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(100.dp)
                .aspectRatio(COVER_ASPECT_RATIO)
                .clip(RoundedCornerShape(10.dp))
                .background(Ink)
                .border(
                    width = 2.dp,
                    color = if (selected) SignalOrange else Line,
                    shape = RoundedCornerShape(10.dp)
                )
                .clickable(onClick = onClick)
        ) {
            Image(
                painter = painterResource(item.res),
                contentDescription = item.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        Text(
            text = item.label,
            style = NameplateStyle,
            fontSize = 9.sp,
            color = if (selected) TextMid else TextLow,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
