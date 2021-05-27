/*
 * Copyright (c) 2020, nwillc@gmail.com
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package com.github.nwillc.ksvg.elements

import com.github.nwillc.ksvg.attributes.AttributeProperty
import com.github.nwillc.ksvg.attributes.AttributeType

/**
 * An SVG circle element.
 */
class CIRCLE(validation: Boolean = false) : Region("circle", validation) {
    /**
     * The x coordinate of the circle's center.
     */
    var cx: String? by AttributeProperty(type = AttributeType.LengthOrPercentage)

    /**
     * The r coordinate of the circle's center.
     */
    var cy: String? by AttributeProperty(type = AttributeType.LengthOrPercentage)

    /**
     * The radius or the circle.
     */
    var r: String? by AttributeProperty(type = AttributeType.Length)
}
