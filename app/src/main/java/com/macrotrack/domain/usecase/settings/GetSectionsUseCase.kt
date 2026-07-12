package com.macrotrack.domain.usecase.settings

import com.macrotrack.data.repository.SectionRepository
import com.macrotrack.domain.model.Section
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSectionsUseCase @Inject constructor(
    private val sectionRepository: SectionRepository
) {
    operator fun invoke(): Flow<List<Section>> {
        return sectionRepository.getAllSections()
    }
}
