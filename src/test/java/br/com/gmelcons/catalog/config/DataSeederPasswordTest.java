package br.com.gmelcons.catalog.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Senha gerada para usuários de demonstração")
class DataSeederPasswordTest {

    @Test
    @DisplayName("tem 16 caracteres e muda a cada chamada")
    void generatedPasswordsAreLongAndDifferent() {
        String a = DataSeeder.randomPassword();
        String b = DataSeeder.randomPassword();
        assertThat(a).hasSize(16);
        assertThat(b).hasSize(16);
        assertThat(a).isNotEqualTo(b);
    }
}
