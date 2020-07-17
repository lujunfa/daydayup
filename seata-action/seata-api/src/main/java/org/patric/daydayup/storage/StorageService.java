package org.patric.daydayup.storage;


public interface StorageService {


    /**
     * deduct storage count
     */
    void deduct(String commodityCode, int count);
}
