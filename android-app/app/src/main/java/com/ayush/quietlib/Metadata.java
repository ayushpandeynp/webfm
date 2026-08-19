package com.ayush.quietlib;

import java.util.Arrays;

public class Metadata {
    public String type;
    public String url;
    public int[] partitionSizes;
    public String checksum;

    public int width;
    public int height;

    public Metadata(String type, String url, String checksum, String pSizes, int width, int height) {
        this.type = type;
        this.url = url;
        this.checksum = checksum;
        this.width = width;
        this.height = height;

        String[] pS = pSizes.split(",");
        partitionSizes = new int[pS.length];
        partitionSizes = Arrays.stream(pS)
                .mapToInt(Integer::parseInt)
                .toArray();
    }
}
