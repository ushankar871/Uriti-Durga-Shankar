package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.model.Product
import com.example.model.ProductCategory

private sealed interface AutocompleteSuggestion {
    data class CategoryMatch(
        val category: ProductCategory,
        val matchingCount: Int
    ) : AutocompleteSuggestion

    data class ItemMatch(
        val product: Product
    ) : AutocompleteSuggestion
}

@Composable
fun SearchAutocompleteField(
    query: String,
    onQueryChange: (String) -> Unit,
    products: List<Product>,
    onSelectCategory: (ProductCategory) -> Unit,
    onSelectProduct: (Product) -> Unit,
    label: String,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }
    var suppressDropdownForQuery by remember { mutableStateOf<String?>(null) }

    val trimmedQuery = query.trim()

    val suggestions = remember(trimmedQuery, products) {
        if (trimmedQuery.isEmpty()) {
            emptyList()
        } else {
            val categoryMatches = ProductCategory.entries
                .filter { cat ->
                    val keywords = when (cat) {
                        ProductCategory.PUJA -> listOf("puja", "pooja", "samagri", "diya", "camphor", "incense", "kumkum")
                        ProductCategory.FANCY -> listOf("fancy", "bangle", "bangles", "bindi", "jhumka", "earring", "potli", "mirror", "gift", "ribbon")
                        ProductCategory.GROCERY -> listOf("grocery", "groceries", "dal", "rice", "ghee", "oil", "spice", "flour")
                    }
                    cat.displayName.contains(trimmedQuery, ignoreCase = true) ||
                        cat.name.contains(trimmedQuery, ignoreCase = true) ||
                        keywords.any { it.contains(trimmedQuery, ignoreCase = true) || trimmedQuery.contains(it, ignoreCase = true) }
                }
                .map { cat ->
                    AutocompleteSuggestion.CategoryMatch(
                        category = cat,
                        matchingCount = products.count { it.categoryEnum() == cat }
                    )
                }

            val prefixItems = products.filter {
                it.name.startsWith(trimmedQuery, ignoreCase = true)
            }
            val containsItems = products.filter { p ->
                !p.name.startsWith(trimmedQuery, ignoreCase = true) && (
                    p.name.contains(trimmedQuery, ignoreCase = true) ||
                        p.description.contains(trimmedQuery, ignoreCase = true) ||
                        p.categoryEnum().displayName.contains(trimmedQuery, ignoreCase = true) ||
                        p.unit.contains(trimmedQuery, ignoreCase = true)
                    )
            }

            val itemMatches = (prefixItems + containsItems)
                .distinctBy { it.id.ifBlank { it.name } }
                .take(6)
                .map { AutocompleteSuggestion.ItemMatch(it) }

            categoryMatches + itemMatches
        }
    }

    val popularQuickPicks = remember(products) {
        products.filter { it.inStock }.take(3).map { it.name }
    }

    val showDropdown = isFocused &&
        trimmedQuery.isNotEmpty() &&
        suppressDropdownForQuery != trimmedQuery &&
        suggestions.isNotEmpty()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { next ->
                suppressDropdownForQuery = null
                onQueryChange(next)
            },
            label = { Text(label) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            suppressDropdownForQuery = null
                            onQueryChange("")
                        },
                        modifier = Modifier.testTag("${testTag}_clear")
                    ) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    suppressDropdownForQuery = trimmedQuery
                    focusManager.clearFocus()
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                }
                .testTag(testTag)
        )

        if (isFocused && trimmedQuery.isEmpty() && popularQuickPicks.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Try:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                popularQuickPicks.forEach { itemName ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier
                            .clickable {
                                suppressDropdownForQuery = itemName
                                onQueryChange(itemName)
                                focusManager.clearFocus()
                            }
                            .testTag("${testTag}_quick_${itemName.lowercase().replace(' ', '_')}")
                    ) {
                        Text(
                            text = itemName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = showDropdown) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("${testTag}_suggestions_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    suggestions.forEachIndexed { index, suggestion ->
                        when (suggestion) {
                            is AutocompleteSuggestion.CategoryMatch -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            suppressDropdownForQuery = trimmedQuery
                                            onSelectCategory(suggestion.category)
                                            onQueryChange("")
                                            focusManager.clearFocus()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                        .testTag("${testTag}_cat_suggestion_${suggestion.category.name.lowercase()}"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Category,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text(
                                                text = highlightQueryMatch(
                                                    text = "Category: ${suggestion.category.displayName}",
                                                    query = trimmedQuery,
                                                    highlightColor = MaterialTheme.colorScheme.primary
                                                ),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "${suggestion.matchingCount} item${if (suggestion.matchingCount == 1) "" else "s"} • Tap to filter",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "Category",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            is AutocompleteSuggestion.ItemMatch -> {
                                val product = suggestion.product
                                val category = product.categoryEnum()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            suppressDropdownForQuery = product.name
                                            onSelectProduct(product)
                                            focusManager.clearFocus()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                        .testTag("${testTag}_item_suggestion_${product.id}"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = when (category) {
                                                ProductCategory.PUJA -> Icons.Filled.LocalFlorist
                                                ProductCategory.FANCY -> Icons.Filled.AutoAwesome
                                                ProductCategory.GROCERY -> Icons.Filled.ShoppingBasket
                                            },
                                            contentDescription = null,
                                            tint = when (category) {
                                                ProductCategory.PUJA -> MaterialTheme.colorScheme.primary
                                                ProductCategory.FANCY -> MaterialTheme.colorScheme.secondary
                                                ProductCategory.GROCERY -> MaterialTheme.colorScheme.tertiary
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = highlightQueryMatch(
                                                    text = product.name,
                                                    query = trimmedQuery,
                                                    highlightColor = MaterialTheme.colorScheme.primary
                                                ),
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${category.displayName} • ₹${product.priceInr.toInt()} / ${product.unit} • ${
                                                    if (product.inStock) "In Stock" else "Out of Stock"
                                                }",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (product.inStock) {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                } else {
                                                    MaterialTheme.colorScheme.error
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Filled.NorthWest,
                                        contentDescription = "Apply search suggestion",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        if (index < suggestions.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}

private fun highlightQueryMatch(
    text: String,
    query: String,
    highlightColor: androidx.compose.ui.graphics.Color
): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    val startIndex = text.indexOf(query, ignoreCase = true)
    if (startIndex < 0) return AnnotatedString(text)
    val endIndex = (startIndex + query.length).coerceAtMost(text.length)
    return buildAnnotatedString {
        append(text.substring(0, startIndex))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = highlightColor)) {
            append(text.substring(startIndex, endIndex))
        }
        append(text.substring(endIndex))
    }
}
