package org.knowledgeroot.app.page.export;

/** An attachment image embedded in an export: the bytes as stored, the type and the pixel size. */
public record ExportImage(byte[] bytes, String mediaType, String name, int width, int height) {

    /** Width and height scaled down to at most {@code maxWidth} pixels, keeping the aspect ratio. */
    public int[] fit(int maxWidth) {
        if (width <= maxWidth || width <= 0) return new int[]{Math.max(width, 1), Math.max(height, 1)};
        return new int[]{maxWidth, Math.max(1, (int) Math.round(height * (maxWidth / (double) width)))};
    }
}
