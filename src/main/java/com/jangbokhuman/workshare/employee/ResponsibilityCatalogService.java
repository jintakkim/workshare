package com.jangbokhuman.workshare.employee;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class ResponsibilityCatalogService {
    private final ResponsibilityOptionRepository responsibilityOptionRepository;

    @Transactional(readOnly = true)
    public List<String> getChoices() {
        return responsibilityOptionRepository.findAllByOrderByTitleAsc().stream()
                .map(ResponsibilityOption::getTitle)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> requireChoices(List<String> selected) {
        List<String> titles =
                selected == null
                        ? List.<String>of()
                        : selected.stream().map(String::strip).distinct().toList();
        if (!titles.isEmpty()
                && responsibilityOptionRepository.findByTitleIn(titles).size() != titles.size())
            throw new IllegalArgumentException("목록에서 담당 업무를 선택해주세요.");
        return titles;
    }

    public void add(String title) {
        if (title == null || title.isBlank() || title.strip().length() > 60)
            throw new IllegalArgumentException("업무 이름은 1~60자로 입력해주세요.");
        String normalizedTitle = title.strip();
        if (responsibilityOptionRepository.existsById(normalizedTitle))
            throw new IllegalArgumentException("이미 등록된 업무입니다.");
        responsibilityOptionRepository.save(new ResponsibilityOption(normalizedTitle));
    }
}
