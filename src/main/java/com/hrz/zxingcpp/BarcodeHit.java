
package com.hrz.zxingcpp;

/**
 * 一次条码识别结果。
 * <p>
 * position 按照左上、右上、右下、左下的顺序保存四个顶点。
 */
public record BarcodeHit(
        byte[] rawBytes,
        String text,
        String format,
        String symbology,
        ImagePoint[] position,
        int orientation,
        boolean mirrored,
        boolean inverted,
        boolean hasECI,
        int sequenceSize,
        int sequenceIndex,
        String sequenceId,
        String symbologyIdentifier,
        String error
) {

    public BarcodeHit {
        rawBytes = rawBytes == null ? new byte[0] : rawBytes.clone();
        position = position == null ? new ImagePoint[0] : position.clone();
    }

    @Override
    public byte[] rawBytes() {
        return rawBytes.clone();
    }

    @Override
    public ImagePoint[] position() {
        return position.clone();
    }

    public boolean isValid() {
        return error == null || error.isEmpty();
    }

    public boolean isPartOfSequence() {
        return sequenceSize > -1 && sequenceIndex > -1;
    }
}
