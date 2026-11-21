package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.Product
import com.example.model.ProductSortOption
import com.example.model.ProductSubCategory
import com.example.model.inferSubCategory

@Composable
fun ProductFilterSortBar(
    allProducts: List<Product>,
    selectedSubCategory: ProductSubCategory,
    sortOption: ProductSortOption,
    inStockOnly: Boolean,
    hasActiveSearch: Boolean,
    onSelectSubCategory: (ProductSubCategory) -> Unit,
    onSelectSortOption: (ProductSortOption) -> Unit,
    onToggleInStockOnly: (Boolean) -> Unit,
    onResetAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }

    val subCategoryCounts = remember(allProducts) {
        ProductSubCategory.filterableEntries.associateWith { sub ->
            if (sub == ProductSubCategory.ALL) allProducts.size
            else allProducts.count { it.inferSubCategory() == sub }
        }
    }

    val hasAnyFilter = selectedSubCategory != ProductSubCategory.ALL ||
        sortOption != ProductSortOption.FEATURED ||
        inStockOnly ||
        hasActiveSearch

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("product_filter_sort_bar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Filter & Sort",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = inStockOnly,
                        onClick = { onToggleInStockOnly(!inStockOnly) },
                        leadingIcon = if (inStockOnly) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        } else null,
                        label = { Text("In Stock") },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 40.dp)
                            .testTag("filter_in_stock_chip")
                    )

                    Box {
                        FilterChip(
                            selected = sortOption != ProductSortOption.FEATURED,
                            onClick = { showSortMenu = true },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "Sort products",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text(sortOption.label) },
                            modifier = Modifier
                                .defaultMinSize(minHeight = 40.dp)
                                .testTag("product_sort_dropdown_button")
                        )
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            ProductSortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    leadingIcon = {
                                        if (sortOption == option) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    onClick = {
                                        onSelectSortOption(option)
                                        showSortMenu = false
                                    },
                                    modifier = Modifier.testTag("sort_option_${option.name}")
                                )
                            }
                        }
                    }
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(ProductSubCategory.filterableEntries, key = { it.name }) { subCategory ->
                    val count = subCategoryCounts[subCategory] ?: 0
                    val isSelected = selectedSubCategory == subCategory
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onSelectSubCategory(
                                if (isSelected && subCategory != ProductSubCategory.ALL) {
                                    ProductSubCategory.ALL
                                } else {
                                    subCategory
                                }
                            )
                        },
                        label = { Text("${subCategory.label} ($count)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 44.dp)
                            .testTag("subcategory_chip_${subCategory.name}")
                    )
                }
            }

            if (hasAnyFilter) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onResetAll,
                        modifier = Modifier.testTag("clear_catalog_filters_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset Filters")
                    }
                }
            }
        }
    }
}
