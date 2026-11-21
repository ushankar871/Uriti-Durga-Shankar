package com.example.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ProductSubCategory
import kotlinx.coroutines.delay

data class PromoBanner(
    val id: String,
    val badge: String,
    val title: String,
    val subtitle: String,
    val ctaLabel: String,
    val targetSubCategory: ProductSubCategory,
    @DrawableRes val imageRes: Int,
    val accentColor: Color,
    val icon: ImageVector
)

fun defaultPromotionalBanners(): List<PromoBanner> = listOf(
    PromoBanner(
        id = "puja_festival_offer",
        badge = "SEASONAL PUJA OFFER",
        title = "Festive Aarti & Brass Diya Specials",
        subtitle = "Save up to 15% on pure Bhimseni camphor, brass diyas & temple agarbatti packs.",
        ctaLabel = "Shop Puja Samagri",
        targetSubCategory = ProductSubCategory.PUJA_SAMAGRI,
        imageRes = R.drawable.img_puja_banner,
        accentColor = Color(0xFFD97706),
        icon = Icons.Filled.AutoAwesome
    ),
    PromoBanner(
        id = "festive_fancy_collection",
        badge = "NEW FANCY COLLECTION",
        title = "Silk Bangles, Potli Bags & Jhumkas",
        subtitle = "Festive bridal bangles, stone bindis & embroidered return-gift potlis ready now.",
        ctaLabel = "Shop Fancy Items",
        targetSubCategory = ProductSubCategory.FANCY_ITEMS,
        imageRes = R.drawable.img_fancy_items,
        accentColor = Color(0xFF9333EA),
        icon = Icons.Filled.AutoAwesome
    ),
    PromoBanner(
        id = "morning_produce_deals",
        badge = "TRENDING GROCERY DEAL",
        title = "Morning Farm-Fresh Produce Basket",
        subtitle = "Hand-picked tomatoes, palak & coriander greens harvested daily for your kitchen.",
        ctaLabel = "Explore Fresh Produce",
        targetSubCategory = ProductSubCategory.FRESH_PRODUCE,
        imageRes = R.drawable.img_grocery_produce,
        accentColor = Color(0xFF1B6B46),
        icon = Icons.Filled.Eco
    ),
    PromoBanner(
        id = "herbal_personal_care",
        badge = "AYURVEDIC CARE PICKS",
        title = "Herbal Neem, Tulsi & Coconut Care",
        subtitle = "Gentle daily personal care essentials crafted with natural botanicals.",
        ctaLabel = "View Personal Care",
        targetSubCategory = ProductSubCategory.PERSONAL_CARE,
        imageRes = R.drawable.img_personal_care,
        accentColor = Color(0xFFB33F1D),
        icon = Icons.Filled.Spa
    ),
    PromoBanner(
        id = "pantry_value_combo",
        badge = "PANTRY VALUE PACKS",
        title = "Staple Grains, Toor Dal & A2 Cow Ghee",
        subtitle = "Neighborhood wholesale savings on aged basmati rice & unpolished dals.",
        ctaLabel = "Browse Pantry",
        targetSubCategory = ProductSubCategory.PANTRY_STAPLES,
        imageRes = R.drawable.img_pantry_staples,
        accentColor = Color(0xFF9A3412),
        icon = Icons.Filled.LocalOffer
    )
)

@Composable
fun PromotionalBannerCarousel(
    banners: List<PromoBanner> = defaultPromotionalBanners(),
    onBannerClick: (PromoBanner) -> Unit,
    modifier: Modifier = Modifier
) {
    if (banners.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { banners.size })

    LaunchedEffect(pagerState.settledPage, pagerState.isScrollInProgress, banners.size) {
        if (!pagerState.isScrollInProgress && banners.size > 1) {
            delay(4500L)
            val nextPage = (pagerState.currentPage + 1) % banners.size
            pagerState.animateScrollToPage(nextPage)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("promo_banner_carousel"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(end = 24.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val banner = banners[page]
            PromoBannerCard(
                banner = banner,
                onClick = { onBannerClick(banner) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            banners.forEachIndexed { index, banner ->
                val selected = pagerState.currentPage == index
                val indicatorWidth by animateDpAsState(
                    targetValue = if (selected) 22.dp else 7.dp,
                    animationSpec = tween(220),
                    label = "banner_indicator_width"
                )
                val indicatorColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    animationSpec = tween(220),
                    label = "banner_indicator_color"
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(7.dp)
                        .width(indicatorWidth)
                        .clip(CircleShape)
                        .background(indicatorColor)
                )
            }
        }
    }
}

@Composable
private fun PromoBannerCard(
    banner: PromoBanner,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
            .clickable(onClick = onClick)
            .testTag("promo_banner_${banner.id}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = banner.imageRes),
                contentDescription = banner.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xEE1F130B),
                                Color(0xCC2C1A0E),
                                Color(0x661F130B)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = banner.accentColor.copy(alpha = 0.92f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = banner.icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = banner.badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = banner.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = banner.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.88f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = banner.ctaLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF23170F)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color(0xFF23170F),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
