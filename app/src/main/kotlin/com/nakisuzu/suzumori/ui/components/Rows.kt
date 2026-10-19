package com.nakisuzu.suzumori.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nakisuzu.suzumori.ui.theme.SuzumoriTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text

/**
 * 圓角卡片。有 [onClick] 時可點擊，按下時微微縮小。
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    color: Color = SuzumoriTheme.colors.surface,
    shape: Shape = RoundedCornerShape(24.dp),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "press",
    )
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(color)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/** 圓角方塊裡的圖示。 */
@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    background: Color = SuzumoriTheme.colors.surfaceVariant,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.35f))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(iconSize), tint = tint)
    }
}

/** 分頁最上方的大標題，靠左；右側可放按鈕。 */
@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            color = SuzumoriTheme.colors.onSurface,
        )
        actions()
    }
}

/** 區塊標題，右側可放連結。 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = SuzumoriTheme.colors.onSurface,
        )
        trailing()
    }
}

/** 區塊下方的補充說明。 */
@Composable
fun Footnote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        fontSize = 12.sp,
        lineHeight = 19.sp,
        color = SuzumoriTheme.colors.onSurfaceMuted,
    )
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(SuzumoriTheme.colors.divider),
    )
}

/** 帶圓點的狀態標籤，顏色與文字變化時會淡換。 */
@Composable
fun StatusChip(text: String, color: Color, container: Color, modifier: Modifier = Modifier) {
    val fg by animateColorAsState(color, tween(450), label = "chipColor")
    val bg by animateColorAsState(container, tween(450), label = "chipContainer")
    Row(
        modifier = modifier
            .height(28.dp)
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(fg),
        )
        AnimatedText(text = text, color = fg, fontSize = 12, fontWeight = FontWeight.SemiBold)
    }
}

/** 文字改變時淡出舊的、淡入新的。 */
@Composable
fun AnimatedText(
    text: String,
    color: Color,
    fontSize: Int,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Normal,
    lineHeight: Int? = null,
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) using SizeTransform(clip = false) },
        modifier = modifier,
        label = "text",
    ) { value ->
        Text(
            text = value,
            fontSize = fontSize.sp,
            fontWeight = fontWeight,
            color = color,
            lineHeight = lineHeight?.sp ?: (fontSize * 1.4f).sp,
        )
    }
}

/**
 * 卡片中的一列：左邊圖示與標題，右邊放值或箭頭。
 *
 * 卡片本身不留左右邊距，由這一列內縮 [horizontalPadding]，按下時的反白才會延伸到卡片兩側；
 * 可點擊時和 [Panel] 一樣按下會微微縮小。
 */
@Composable
fun InfoRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    showDivider: Boolean = true,
    horizontalPadding: Dp = 18.dp,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val colors = SuzumoriTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "rowPress",
    )
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .then(
                    if (onClick != null) {
                        Modifier
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clickable(
                                interactionSource = interaction,
                                indication = LocalIndication.current,
                                role = Role.Button,
                                onClick = onClick,
                            )
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = horizontalPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconBadge(icon = icon, tint = iconTint)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(text = title, fontSize = 15.sp, color = colors.onSurface)
                if (summary != null) {
                    Text(text = summary, fontSize = 12.sp, lineHeight = 17.sp, color = colors.onSurfaceMuted)
                }
            }
            trailing()
        }
        if (showDivider) Divider(Modifier.padding(horizontal = horizontalPadding))
    }
}

/** 列尾的箭頭。 */
@Composable
fun Chevron(icon: ImageVector = SzIcons.ChevronRight) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = SuzumoriTheme.colors.onSurfaceMuted,
    )
}

/** 列尾的值，顏色變化時淡換。 */
@Composable
fun ValueText(text: String, color: Color) {
    val animated by animateColorAsState(color, tween(450), label = "value")
    AnimatedText(text = text, color = animated, fontSize = 14, fontWeight = FontWeight.SemiBold)
}

/**
 * 標題、說明與開關，整列都可以點。
 *
 * [enabled] 為 false 時淡成半透明並停用；[locked] 只暫時擋下操作、外觀不變，
 * 用在其他設定寫入中的短暫期間，避免整排開關閃一下。
 */
@Composable
fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showDivider: Boolean,
    locked: Boolean = false,
) {
    val colors = SuzumoriTheme.colors
    val alpha by animateFloatAsState(if (enabled) 1f else 0.45f, tween(300), label = "rowAlpha")
    val onChange: (Boolean) -> Unit = { if (!locked) onCheckedChange(it) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { this.alpha = alpha }
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    interactionSource = null,
                    indication = null,
                    onValueChange = onChange,
                )
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
                Text(text = summary, fontSize = 12.5.sp, lineHeight = 20.sp, color = colors.onSurfaceMuted)
            }
            Spacer(Modifier.width(16.dp))
            Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
        }
        if (showDivider) Divider()
    }
}

/** 圓角方形的圖示按鈕，用在頁首。 */
@Composable
fun SquareIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Panel(
        modifier = modifier.size(44.dp),
        shape = RoundedCornerShape(15.dp),
        verticalArrangement = Arrangement.Center,
        onClick = onClick,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier
                .size(20.dp)
                .align(Alignment.CenterHorizontally),
            tint = SuzumoriTheme.colors.onSurface,
        )
    }
}
