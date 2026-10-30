package io.nekohasekai.sagernet.glass

/**
 * Separable box blur, run three times: a cheap, allocation-light approximation of a Gaussian.
 *
 * The backdrop is blurred while downscaled (see [GlassBackdrop.downscale]), so the working set is
 * roughly 250x550 pixels on a 1080p phone: about 1.5 ms for three passes. RenderScript is
 * deprecated and RenderEffect only exists from API 31, so a plain integer box blur keeps one code
 * path for API 24 through 35 and gives the same look on every device.
 */
internal object GlassBlur {

    /**
     * Blurs [pixels] in place.
     *
     * @param radius radius in pixels of the (already downscaled) bitmap.
     * @param passes 3 for a Gaussian-like falloff, 2 when the frame budget is tight.
     */
    fun blur(pixels: IntArray, width: Int, height: Int, radius: Int, passes: Int = 3) {
        if (radius < 1 || width < 2 || height < 2 || pixels.size < width * height) return
        val tmp = IntArray(width * height)
        repeat(passes) {
            horizontal(pixels, tmp, width, height, radius)
            vertical(tmp, pixels, width, height, radius)
        }
    }

    private fun horizontal(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val sumA = IntArray(w + 1)
        val sumR = IntArray(w + 1)
        val sumG = IntArray(w + 1)
        val sumB = IntArray(w + 1)
        for (y in 0 until h) {
            val row = y * w
            var a = 0
            var rd = 0
            var gn = 0
            var bl = 0
            for (x in 0 until w) {
                val c = src[row + x]
                a += (c ushr 24) and 0xFF
                rd += (c ushr 16) and 0xFF
                gn += (c ushr 8) and 0xFF
                bl += c and 0xFF
                sumA[x + 1] = a
                sumR[x + 1] = rd
                sumG[x + 1] = gn
                sumB[x + 1] = bl
            }
            for (x in 0 until w) {
                val x0 = if (x > r) x - r else 0
                val x1 = if (x + r < w) x + r else w - 1
                val count = x1 - x0 + 1
                dst[row + x] = (((sumA[x1 + 1] - sumA[x0]) / count) shl 24) or
                        (((sumR[x1 + 1] - sumR[x0]) / count) shl 16) or
                        (((sumG[x1 + 1] - sumG[x0]) / count) shl 8) or
                        ((sumB[x1 + 1] - sumB[x0]) / count)
            }
        }
    }

    private fun vertical(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val sumA = IntArray(h + 1)
        val sumR = IntArray(h + 1)
        val sumG = IntArray(h + 1)
        val sumB = IntArray(h + 1)
        for (x in 0 until w) {
            var a = 0
            var rd = 0
            var gn = 0
            var bl = 0
            for (y in 0 until h) {
                val c = src[y * w + x]
                a += (c ushr 24) and 0xFF
                rd += (c ushr 16) and 0xFF
                gn += (c ushr 8) and 0xFF
                bl += c and 0xFF
                sumA[y + 1] = a
                sumR[y + 1] = rd
                sumG[y + 1] = gn
                sumB[y + 1] = bl
            }
            for (y in 0 until h) {
                val y0 = if (y > r) y - r else 0
                val y1 = if (y + r < h) y + r else h - 1
                val count = y1 - y0 + 1
                dst[y * w + x] = (((sumA[y1 + 1] - sumA[y0]) / count) shl 24) or
                        (((sumR[y1 + 1] - sumR[y0]) / count) shl 16) or
                        (((sumG[y1 + 1] - sumG[y0]) / count) shl 8) or
                        ((sumB[y1 + 1] - sumB[y0]) / count)
            }
        }
    }
}
