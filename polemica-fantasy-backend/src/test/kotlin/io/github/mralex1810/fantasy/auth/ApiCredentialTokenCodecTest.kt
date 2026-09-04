package io.github.mralex1810.fantasy.auth

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApiCredentialTokenCodecTest {
    private val codec = ApiCredentialTokenCodec()

    @Test
    fun `generated tokens are well formed unique and hashed`() {
        val first = codec.generate()
        val second = codec.generate()

        assertThat(first).startsWith("pfa_").hasSize(47)
        assertThat(codec.isWellFormed(first)).isTrue()
        assertThat(second).isNotEqualTo(first)
        assertThat(codec.hash(first)).hasSize(64).doesNotContain(first)
        assertThat(codec.hint(first)).isEqualTo(first.take(12))
    }

    @Test
    fun `malformed tokens are rejected before lookup`() {
        assertThat(codec.isWellFormed("")).isFalse()
        assertThat(codec.isWellFormed("pfa_short")).isFalse()
        assertThat(codec.isWellFormed("other_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")).isFalse()
        assertThat(codec.isWellFormed("pfa_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA!" )).isFalse()
    }
}
