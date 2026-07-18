package com.macrotrack.domain.usecase.settings

import com.macrotrack.data.repository.SectionRepository
import com.macrotrack.domain.model.Section
import javax.inject.Inject

class UpdateSectionsUseCase @Inject constructor(
    private val sectionRepository: SectionRepository
) {
    suspend operator fun invoke(sections: List<Section>) {
        sectionRepository.updateAll(sections)
    }
}