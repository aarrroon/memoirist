package com.memoirist.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/** {@link PageResponse#of} copies the page numbers from Spring's {@code Page} and takes the rows it is given. */
class PageResponseTest {

    @Test
    void itCarriesThePageNumbersAndTheMappedRows() {
        PageImpl<Integer> source = new PageImpl<>(List.of(3, 4), PageRequest.of(1, 2), 5);

        PageResponse<String> response = PageResponse.of(source, List.of("three", "four"));

        assertThat(response.items()).containsExactly("three", "four");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalItems()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @Test
    void anEmptyListHasZeroPages() {
        PageResponse<String> response =
                PageResponse.of(new PageImpl<Integer>(List.of(), PageRequest.of(0, 10), 0), List.of());

        assertThat(response.items()).isEmpty();
        assertThat(response.totalItems()).isZero();
        assertThat(response.totalPages()).isZero();
    }

    @Test
    void aPagePastTheEndKeepsTheTotals() {
        PageResponse<String> response =
                PageResponse.of(new PageImpl<Integer>(List.of(), PageRequest.of(5, 2), 5), List.of());

        assertThat(response.items()).isEmpty();
        assertThat(response.page()).isEqualTo(5);
        assertThat(response.totalItems()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
    }
}
