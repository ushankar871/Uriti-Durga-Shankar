package com.example.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.model.Product
import com.example.model.ProductCategory
import java.util.Locale

data class GeneralItemImagePreset(
    val tag: String,
    val label: String,
    val suggestedCategory: ProductCategory,
    @DrawableRes val drawableRes: Int
)

fun generalItemImagePresets(): List<GeneralItemImagePreset> = listOf(
    GeneralItemImagePreset(
        tag = "general",
        label = "General Store",
        suggestedCategory = ProductCategory.GROCERY,
        drawableRes = R.drawable.img_general_merchandise
    ),
    GeneralItemImagePreset(
        tag = "fancy",
        label = "Fancy & Bangles",
        suggestedCategory = ProductCategory.FANCY,
        drawableRes = R.drawable.img_fancy_items
    ),
    GeneralItemImagePreset(
        tag = "diya",
        label = "Puja Samagri",
        suggestedCategory = ProductCategory.PUJA,
        drawableRes = R.drawable.img_puja_samagri
    ),
    GeneralItemImagePreset(
        tag = "produce",
        label = "Fresh Produce",
        suggestedCategory = ProductCategory.GROCERY,
        drawableRes = R.drawable.img_grocery_produce
    ),
    GeneralItemImagePreset(
        tag = "rice",
        label = "Pantry Staples",
        suggestedCategory = ProductCategory.GROCERY,
        drawableRes = R.drawable.img_pantry_staples
    ),
    GeneralItemImagePreset(
        tag = "personal_care",
        label = "Personal Care",
        suggestedCategory = ProductCategory.GROCERY,
        drawableRes = R.drawable.img_personal_care
    ),
    GeneralItemImagePreset(
        tag = "puja_banner",
        label = "Festive Aarti",
        suggestedCategory = ProductCategory.PUJA,
        drawableRes = R.drawable.img_puja_banner
    )
)

@DrawableRes
fun resolveProductArtworkRes(
    imageTag: String,
    category: ProductCategory
): Int {
    return when (imageTag.trim().lowercase(Locale.US)) {
        "fancy", "bangles", "bindi", "jhumka", "gift", "potli" -> R.drawable.img_fancy_items
        "produce", "vegetables", "fruits", "greens" -> R.drawable.img_grocery_produce
        "rice", "dal", "ghee", "spices", "staples", "pantry" -> R.drawable.img_pantry_staples
        "personal_care", "soap", "hygiene" -> R.drawable.img_personal_care
        "diya", "camphor", "incense", "flowers", "kumkum" -> R.drawable.img_puja_samagri
        "puja_banner" -> R.drawable.img_puja_banner
        "general", "household", "stationery" -> R.drawable.img_general_merchandise
        else -> when (category) {
            ProductCategory.PUJA -> R.drawable.img_puja_samagri
            ProductCategory.FANCY -> R.drawable.img_fancy_items
            ProductCategory.GROCERY -> R.drawable.img_grocery_produce
        }
    }
}

@Composable
fun ProductThumbnail(
    product: Product,
    modifier: Modifier = Modifier
) {
    val category = product.categoryEnum()
    val fallbackRes = resolveProductArtworkRes(product.imageTag, category)
    val saturationFilter = if (product.inStock) {
        null
    } else {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.22f) })
    }

    val badgeIcon = when (category) {
        ProductCategory.PUJA -> Icons.Filled.LocalFlorist
        ProductCategory.FANCY -> Icons.Filled.AutoAwesome
        ProductCategory.GROCERY -> Icons.Filled.ShoppingBasket
    }
    val badgeBg = when (category) {
        ProductCategory.PUJA -> MaterialTheme.colorScheme.secondaryContainer
        ProductCategory.FANCY -> MaterialTheme.colorScheme.primaryContainer
        ProductCategory.GROCERY -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val badgeTint = when (category) {
        ProductCategory.PUJA -> MaterialTheme.colorScheme.onSecondaryContainer
        ProductCategory.FANCY -> MaterialTheme.colorScheme.onPrimaryContainer
        ProductCategory.GROCERY -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (product.photoUri.isNotBlank()) {
            AsyncImage(
                model = product.photoUri,
                contentDescription = product.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                colorFilter = saturationFilter,
                error = painterResource(id = fallbackRes),
                placeholder = painterResource(id = fallbackRes)
            )
        } else {
            Image(
                painter = painterResource(id = fallbackRes),
                contentDescription = product.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                colorFilter = saturationFilter
            )
        }

        if (!product.inStock) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.error
                ) {
                    Text(
                        text = "OUT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(5.dp)
                .size(22.dp),
            shape = CircleShape,
            color = badgeBg
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = badgeIcon,
                    contentDescription = category.displayName,
                    tint = badgeTint,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

