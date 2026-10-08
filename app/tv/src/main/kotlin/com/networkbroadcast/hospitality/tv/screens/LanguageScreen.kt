package com.networkbroadcast.hospitality.tv.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.networkbroadcast.hospitality.brand.BrandConfig
import com.networkbroadcast.hospitality.designsystem.FocusCard
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import com.networkbroadcast.hospitality.tv.R
import com.networkbroadcast.hospitality.brand.UiText

@Composable
fun LanguageScreen(brand: BrandConfig, selected: String?, onSelected: (String) -> Unit) {
    val colors = HospitalityTheme.colors
    val first = rememberInitialFocus()
    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (brand.showLogo) {
            Image(painterResource(R.drawable.brand_logo), contentDescription = brand.displayName, modifier = Modifier.height(72.dp))
        } else {
            Text(brand.displayName, style = HospitalityTheme.typography.display, color = colors.textPrimary)
        }
        // El título va en cada idioma ofrecido: el huésped todavía no eligió el suyo.
        Text(
            text = brand.languages.map { UiText.of(it).chooseLanguage }.distinct().joinToString("  ·  "),
            style = HospitalityTheme.typography.body,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 24.dp, bottom = 32.dp),
        )
        // Desplazable: con 5 o más idiomas la fila no entra en pantalla (RIU ofrece 8).
        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = FocusListPadding) {
            itemsIndexed(brand.languages) { index, code ->
                val isFirst = if (selected != null) code == selected else index == 0
                FocusCard(
                    onClick = { onSelected(code) },
                    modifier = Modifier.size(width = 180.dp, height = 96.dp)
                        .then(if (isFirst) Modifier.focusRequester(first) else Modifier),
                ) {
                    Text(
                        text = UiText.languageNames[code] ?: code.uppercase(),
                        style = HospitalityTheme.typography.label,
                        color = colors.textPrimary,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }
}
