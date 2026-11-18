package io.github.avalonskyafar.mallet.interaction

import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.mallet.interaction.MalletSpatialGraph.Side
import org.junit.Assert.assertThrows
import org.junit.Test

class MalletSpatialGraphTest {

    private fun graph(): MalletSpatialGraph = MalletSpatialGraph.Builder("main")
        .connect("main", Side.BELOW, "settings")
        .connect("main", Side.START, "guide")
        .connect("main", Side.END, "more")
        .connect("settings", Side.END, "advanced")
        .connect("advanced", Side.ABOVE, "main", reciprocal = false)
        .child("more", Side.END, "about")
        .child("more", Side.END, "privacy")
        .build()

    @Test
    fun `connections are reciprocal`() {
        val g = graph()
        assertThat(g.neighbour("main", Side.BELOW)).isEqualTo("settings")
        assertThat(g.neighbour("settings", Side.ABOVE)).isEqualTo("main")
        assertThat(g.neighbour("guide", Side.END)).isEqualTo("main")
        assertThat(g.sideOf("main", "more")).isEqualTo(Side.END)
        assertThat(g.sideOf("more", "main")).isEqualTo(Side.START)
        assertThat(g.neighbour("main", Side.ABOVE)).isNull()
    }

    @Test
    fun `a one way connection exists in one direction only`() {
        val g = graph()
        assertThat(g.neighbour("advanced", Side.ABOVE)).isEqualTo("main")
        assertThat(g.neighbour("main", Side.BELOW)).isEqualTo("settings")
    }

    @Test
    fun `children are placed but cannot be dragged to`() {
        val g = graph()
        assertThat(g.neighbour("more", Side.END)).isNull()
        assertThat(g.sideOf("more", "about")).isEqualTo(Side.END)
        assertThat(g.sideOf("more", "privacy")).isEqualTo(Side.END)
        assertThat(g.neighbour("about", Side.START)).isEqualTo("more")
    }

    @Test
    fun `back follows the page each page was first connected from`() {
        val g = graph()
        assertThat(g.parentOf("main")).isNull()
        assertThat(g.parentOf("settings")).isEqualTo("main")
        assertThat(g.parentOf("advanced")).isEqualTo("settings")
        assertThat(g.parentOf("about")).isEqualTo("more")
    }

    @Test
    fun `an explicit parent overrides the default`() {
        val g = MalletSpatialGraph.Builder("main")
            .connect("main", Side.END, "a")
            .connect("a", Side.END, "b")
            .parent("b", "main")
            .build()
        assertThat(g.parentOf("b")).isEqualTo("main")
    }

    @Test
    fun `two pages cannot share a draggable side`() {
        val builder = MalletSpatialGraph.Builder("main").connect("main", Side.END, "a")
        val error = assertThrows(IllegalArgumentException::class.java) { builder.connect("main", Side.END, "b") }
        assertThat(error).hasMessageThat().contains("END")
    }

    @Test
    fun `a page that cannot get back to the root is rejected`() {
        val builder = MalletSpatialGraph.Builder("main").connect("x", Side.END, "y")
        val error = assertThrows(IllegalArgumentException::class.java) { builder.build() }
        assertThat(error).hasMessageThat().contains("root")
    }

    @Test
    fun `a one way connection also gives the new page its parent`() {
        val g = MalletSpatialGraph.Builder("main")
            .connect("main", Side.END, "more", reciprocal = false)
            .build()
        assertThat(g.parentOf("more")).isEqualTo("main")
        assertThat(g.neighbour("main", Side.END)).isEqualTo("more")
        assertThat(g.neighbour("more", Side.START)).isNull()
    }

    @Test
    fun `sides know their opposite and axis`() {
        assertThat(Side.START.opposite).isEqualTo(Side.END)
        assertThat(Side.ABOVE.opposite).isEqualTo(Side.BELOW)
        assertThat(Side.START.isHorizontal).isTrue()
        assertThat(Side.BELOW.isHorizontal).isFalse()
    }
}
